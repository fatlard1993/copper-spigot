package justfatlard.copper_spigot;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A copper tap that pours one whole source block on demand.
 *
 * <p>Dripstone's rule, made deliberate: what it draws from is the fluid on the far side of the
 * block it hangs from, and what it pours lands directly beneath it. A hanging spigot at P is held
 * by P+up and draws from P+2up; a wall spigot facing out of a wall draws from the block behind
 * the wall block it is fixed to.
 *
 * <p>Water is not used up, as dripstone does not use it up and as two buckets already make it
 * endless. Lava is: the source, or the lava cauldron, is emptied by the pour, so a spigot moves
 * lava and never makes it.
 *
 * <p>It fires on a click, or on the moment a redstone signal arrives. Holding the signal does
 * nothing more; the pour waits for the next rising edge, the way a dispenser does.
 */
public class CopperSpigotBlock extends Block {
	/** Out of the wall for a wall spigot; only turns the handle on a hanging one. */
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty HANGING = BlockStateProperties.HANGING;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	/** Ticks between the signal arriving and the pour, as a dispenser waits for its own. */
	private static final int REDSTONE_DELAY = 2;

	// Each shape is the matching model in assets/.../models/block, box for box, facing north;
	// generate_models.py writes the models from the same numbers.
	private static final Map<Direction, VoxelShape> WALL = Shapes.rotateHorizontal(Shapes.or(
		Block.box(5, 9, 15, 11, 15, 16),
		Block.box(6.5, 10.5, 6, 9.5, 13.5, 15),
		Block.box(6.5, 5, 6, 9.5, 10.5, 9),
		Block.box(6, 3, 5.5, 10, 5, 9.5),
		Block.box(7.5, 13.5, 10, 8.5, 15, 11),
		Block.box(5.5, 15, 10, 10.5, 16, 11)));

	private static final Map<Direction, VoxelShape> HUNG = Shapes.rotateHorizontal(Shapes.or(
		Block.box(5, 15, 5, 11, 16, 11),
		Block.box(6.5, 7, 6.5, 9.5, 15, 9.5),
		Block.box(6, 5, 6, 10, 7, 10),
		Block.box(7.5, 11, 5, 8.5, 12, 6.5),
		Block.box(7.5, 9.5, 4, 8.5, 13.5, 5)));

	/** What a pour came to, for the caller and for the tests. */
	public enum Pour {
		/** A source block or a fuller cauldron is now below. */
		POURED,
		/** Water poured where water boils off, as a bucket's would. */
		EVAPORATED,
		/** Fired, with nothing to draw or nowhere to put it. */
		DRY
	}

	/** A source the spigot can draw on, and whether drawing from it is a cauldron's. */
	public record Source(Fluid fluid, boolean cauldron) {}

	/**
	 * What a pour of a given fluid would do to the spot under the spout.
	 *
	 * <p>Worked out by {@link #planBelow} and nowhere else: the pour carries it out and the
	 * block-tip line reads it out, so what the card promises is what a click does.
	 */
	public enum Plan {
		/** An empty cauldron filled, or a part-filled water cauldron topped up. */
		FILL_CAULDRON,
		/** A waterloggable block takes the water in. */
		WATERLOG,
		/** A source block goes down in the space, as from a bucket. */
		POUR,
		/** Water poured where water boils off. */
		EVAPORATE,
		/** Nowhere for it to go. */
		BLOCKED
	}

	public CopperSpigotBlock(Properties settings) {
		super(settings);
		this.registerDefaultState(this.stateDefinition.any()
			.setValue(FACING, Direction.NORTH)
			.setValue(HANGING, false)
			.setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HANGING, POWERED);
	}

	// --- Attachment -------------------------------------------------------------------------

	/** The way from the spigot to the block it is fixed to. */
	public static Direction towardSupport(BlockState state) {
		return state.getValue(HANGING) ? Direction.UP : state.getValue(FACING).getOpposite();
	}

	public static BlockPos supportPos(BlockState state, BlockPos pos) {
		return pos.relative(towardSupport(state));
	}

	/** The far side of the support, which is where the fluid has to be. */
	public static BlockPos sourcePos(BlockState state, BlockPos pos) {
		return pos.relative(towardSupport(state), 2);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (state.getValue(HANGING)) {
			return Block.canSupportCenter(level, pos.above(), Direction.DOWN);
		}
		Direction facing = state.getValue(FACING);
		BlockPos support = pos.relative(facing.getOpposite());
		return level.getBlockState(support).isFaceSturdy(level, support, facing);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState base = this.defaultBlockState().setValue(POWERED, level.hasNeighborSignal(pos));
		// A hanging spigot's handle turns to whoever hung it.
		Direction toPlayer = context.getHorizontalDirection().getOpposite();

		// The face clicked first, so a tap put on a wall goes on that wall even if the player
		// happened to be looking up at the time; then whatever else is nearest the look, as a
		// wall torch does. Never on a floor: a spigot pointing up pours nothing.
		Direction clicked = context.getClickedFace();
		BlockState preferred = attachedFrom(base, clicked.getOpposite(), toPlayer);
		if (preferred != null && preferred.canSurvive(level, pos)) return preferred;

		for (Direction looking : context.getNearestLookingDirections()) {
			BlockState candidate = attachedFrom(base, looking, toPlayer);
			if (candidate != null && candidate.canSurvive(level, pos)) return candidate;
		}
		return null;
	}

	/** The state fixed to whatever lies {@code toward} the spigot, or null if that is the floor. */
	private static @Nullable BlockState attachedFrom(BlockState base, Direction toward, Direction toPlayer) {
		if (toward == Direction.DOWN) return null;
		if (toward == Direction.UP) return base.setValue(HANGING, true).setValue(FACING, toPlayer);
		return base.setValue(HANGING, false).setValue(FACING, toward.getOpposite());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		// Air here pops it off with its drop, the way a torch goes when its wall does.
		return directionToNeighbour == towardSupport(state) && !state.canSurvive(level, pos)
			? Blocks.AIR.defaultBlockState()
			: state;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return (state.getValue(HANGING) ? HUNG : WALL).get(state.getValue(FACING));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	// --- Triggers ---------------------------------------------------------------------------

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		// A block in hand is being placed, and a bucket is being filled or emptied: both are the
		// item's business, not the tap's. Anything else turns the tap.
		if (stack.getItem() instanceof BlockItem || stack.getItem() instanceof BucketItem) {
			return InteractionResult.PASS;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level instanceof ServerLevel serverLevel) {
			pour(serverLevel, pos, state, player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
			@Nullable Orientation orientation, boolean movedByPiston) {
		if (level.isClientSide()) return;

		boolean signal = level.hasNeighborSignal(pos);
		if (signal == state.getValue(POWERED)) return;

		// Remembered on the state so a held signal is one pour, not one a tick.
		level.setBlock(pos, state.setValue(POWERED, signal), Block.UPDATE_CLIENTS);
		if (signal) {
			level.scheduleTick(pos, this, REDSTONE_DELAY);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		pour(level, pos, state, null);
	}

	// --- The pour ---------------------------------------------------------------------------

	/**
	 * Draw one source block from behind the support and put it down beneath the spout.
	 *
	 * <p>Public so the gametest drives exactly what a click or a signal does.
	 */
	public static Pour pour(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player who) {
		BlockPos from = sourcePos(state, pos);
		Source source = sourceAt(level, from);
		if (source == null) {
			dryClick(level, pos);
			return Pour.DRY;
		}

		BlockPos below = pos.below();
		Pour result = deliver(level, below, source.fluid(), who);
		switch (result) {
			case DRY -> dryClick(level, pos);
			case POURED -> {
				if (source.fluid() == Fluids.LAVA) consume(level, from, source);
				spout(level, pos, source.fluid());
			}
			case EVAPORATED -> {}
		}
		return result;
	}

	/**
	 * What the spigot would draw from {@code pos}, the far side of its support, or null for
	 * nothing it can draw on.
	 */
	public static @Nullable Source sourceAt(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.WATER_CAULDRON) && state.getValue(LayeredCauldronBlock.LEVEL) == LayeredCauldronBlock.MAX_FILL_LEVEL) {
			return new Source(Fluids.WATER, true);
		}
		if (state.is(Blocks.LAVA_CAULDRON)) {
			return new Source(Fluids.LAVA, true);
		}

		// The fluid state rather than the block, so a waterlogged block is water and flowing
		// water, whose type is the flowing kind, is not a source.
		FluidState fluid = level.getFluidState(pos);
		if (fluid.isSourceOfType(Fluids.WATER)) return new Source(Fluids.WATER, false);
		if (fluid.isSourceOfType(Fluids.LAVA)) return new Source(Fluids.LAVA, false);
		return null;
	}

	/** Bucket rules, so a spigot can only pour where a bucket could have. Changes nothing. */
	public static Plan planBelow(Level level, BlockPos pos, Fluid fluid) {
		BlockState target = level.getBlockState(pos);

		if (target.is(Blocks.CAULDRON)) return Plan.FILL_CAULDRON;
		if (target.is(Blocks.WATER_CAULDRON) && fluid == Fluids.WATER
				&& target.getValue(LayeredCauldronBlock.LEVEL) < LayeredCauldronBlock.MAX_FILL_LEVEL) {
			return Plan.FILL_CAULDRON;
		}
		// Full, or holding something else: a cauldron is never poured over.
		if (target.getBlock() instanceof AbstractCauldronBlock) return Plan.BLOCKED;

		// Already that very source: pouring changes nothing.
		if (target.getFluidState().isSourceOfType(fluid)) return Plan.BLOCKED;

		boolean waterlogs = fluid == Fluids.WATER && target.getBlock() instanceof LiquidBlockContainer container
			&& container.canPlaceLiquid(null, level, pos, target, fluid);
		if (!target.isAir() && !target.canBeReplaced(fluid) && !waterlogs) return Plan.BLOCKED;

		if (fluid == Fluids.WATER && level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos)) {
			return Plan.EVAPORATE;
		}
		return waterlogs ? Plan.WATERLOG : Plan.POUR;
	}

	/** Carry out what {@link #planBelow} decided. */
	private static Pour deliver(ServerLevel level, BlockPos pos, Fluid fluid, @Nullable Player who) {
		BlockState target = level.getBlockState(pos);

		switch (planBelow(level, pos, fluid)) {
			case BLOCKED -> {
				return Pour.DRY;
			}
			case EVAPORATE -> {
				RandomSource random = level.getRandom();
				level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
					2.6F + (random.nextFloat() - random.nextFloat()) * 0.8F);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					8, 0.25, 0.25, 0.25, 0.0);
				return Pour.EVAPORATED;
			}
			case FILL_CAULDRON -> {
				fill(level, pos, target.is(Blocks.CAULDRON) && fluid == Fluids.LAVA
					? Blocks.LAVA_CAULDRON.defaultBlockState()
					: Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL),
					fluid, who);
				return Pour.POURED;
			}
			case WATERLOG -> {
				if (!((LiquidBlockContainer) target.getBlock()).placeLiquid(level, pos, target, Fluids.WATER.getSource(false))) {
					return Pour.DRY;
				}
			}
			case POUR -> {
				if (!target.isAir() && !(target.getBlock() instanceof LiquidBlock)) {
					level.destroyBlock(pos, true);
				}
				if (!level.setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), Block.UPDATE_ALL_IMMEDIATE)) {
					return Pour.DRY;
				}
			}
		}
		emptySound(level, pos, fluid, who);
		return Pour.POURED;
	}

	private static void fill(ServerLevel level, BlockPos pos, BlockState filled, Fluid fluid, @Nullable Player who) {
		level.setBlockAndUpdate(pos, filled);
		level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(who, filled));
		emptySound(level, pos, fluid, who);
	}

	private static void emptySound(ServerLevel level, BlockPos pos, Fluid fluid, @Nullable Player who) {
		level.playSound(null, pos, fluid == Fluids.LAVA ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY,
			SoundSource.BLOCKS, 1.0F, 1.0F);
		level.gameEvent(who, GameEvent.FLUID_PLACE, pos);
	}

	/** Lava only: what was poured is gone from where it came from. */
	private static void consume(ServerLevel level, BlockPos pos, Source source) {
		if (source.cauldron()) {
			BlockState empty = Blocks.CAULDRON.defaultBlockState();
			level.setBlockAndUpdate(pos, empty);
			level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(empty));
		} else {
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
		}
	}

	/** A few drops off the spout as it pours. */
	private static void spout(ServerLevel level, BlockPos pos, Fluid fluid) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.15;
		double z = pos.getZ() + 0.5;
		SimpleParticleType falling = fluid == Fluids.LAVA ? ParticleTypes.FALLING_LAVA : ParticleTypes.FALLING_WATER;
		level.sendParticles(falling, x, y, z, 4, 0.06, 0.02, 0.06, 0.0);
		level.sendParticles(fluid == Fluids.LAVA ? ParticleTypes.LAVA : ParticleTypes.SPLASH,
			x, y - 0.6, z, 6, 0.2, 0.05, 0.2, 0.0);
	}

	/** It turned, and nothing came out. */
	private static void dryClick(ServerLevel level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.4F, 1.6F);
	}
}
