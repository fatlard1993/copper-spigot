package justfatlard.copper_spigot.gametest;

import justfatlard.copper_spigot.CopperSpigotBlock;
import justfatlard.copper_spigot.Main;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * This mod loads, a world comes up with it installed, and a spigot pours every way it is meant to.
 *
 * <p>Each rig is built in open air well above the player, a column apart so no rig's spill reaches
 * another's, and each is checked on the same server tick it pours: the water left behind spreads
 * on later ticks, and none of these checks care where it goes.
 */
public final class SmokeTest implements FabricClientGameTest {
	/** Spacing between rigs, wide enough that spreading water never meets the next one. */
	private static final int APART = 10;

	/** From a signal arriving to the end of the turn it starts, with a tick to spare. */
	private static final int REDSTONE_POUR = 2 + CopperSpigotBlock.TURN_TICKS + 2;

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, Main.MOD_ID, session -> {
			// The player is the server's to read, so its position is fetched on the server thread.
			BlockPos[] at = new BlockPos[1];
			session.onServer(server -> at[0] = session.player().blockPosition().above(24));
			BlockPos origin = at[0];

			// Hanging, water above the support, an empty cauldron below: a click turns the handle
			// open, and as it turns shut the cauldron is full to the brim and the water stays where
			// it was.
			BlockPos hungWater = origin;
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				BlockState spigot = hang(level, hungWater, Blocks.WATER.defaultBlockState(), Blocks.CAULDRON.defaultBlockState());
				InteractionResult clicked = spigot.useWithoutItem(level, session.player(),
					new BlockHitResult(Vec3.atCenterOf(hungWater), Direction.NORTH, hungWater, false));
				if (!clicked.consumesAction()) throw new AssertionError("clicking the spigot returned " + clicked);
			});
			session.waitTicks(6);
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				int turn = level.getBlockState(hungWater).getValue(CopperSpigotBlock.TURN);
				if (turn != 3) throw new AssertionError("mid-pour the handle stands at turn " + turn + ", not open");
				if (!level.getBlockState(hungWater.below()).is(Blocks.CAULDRON)) {
					throw new AssertionError("the cauldron filled before the stream ran out: " + level.getBlockState(hungWater.below()));
				}
			});
			session.waitTicks(CopperSpigotBlock.TURN_TICKS);
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				expectFullWaterCauldron(level, hungWater.below(), "hanging spigot under water");
				int turn = level.getBlockState(hungWater).getValue(CopperSpigotBlock.TURN);
				if (turn != 0) throw new AssertionError("after the pour the handle stands at turn " + turn + ", not shut");
				if (!level.getFluidState(hungWater.above(2)).isSourceOfType(Fluids.WATER)) {
					throw new AssertionError("pouring water used up the water source; it should be endless");
				}
			});

			// Hanging, lava above: the cauldron below takes it, and the lava is gone from above.
			BlockPos hungLava = origin.east(APART);
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				BlockState spigot = hang(level, hungLava, Blocks.LAVA.defaultBlockState(), Blocks.CAULDRON.defaultBlockState());
				expectPour(CopperSpigotBlock.pour(level, hungLava, spigot, null), CopperSpigotBlock.Pour.POURED, "lava");
				if (!level.getBlockState(hungLava.below()).is(Blocks.LAVA_CAULDRON)) {
					throw new AssertionError("lava poured into an empty cauldron left " + level.getBlockState(hungLava.below()));
				}
				if (!level.getBlockState(hungLava.above(2)).isAir()) {
					throw new AssertionError("the lava source survived its pour: " + level.getBlockState(hungLava.above(2)));
				}
				// And a second pour has nothing to draw on.
				expectPour(CopperSpigotBlock.pour(level, hungLava, spigot, null), CopperSpigotBlock.Pour.DRY,
					"lava after the source was spent");
			});

			// Hanging from a lava cauldron into open air: a lava source below, an empty cauldron above.
			BlockPos fromCauldron = origin.east(2 * APART);
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				BlockState spigot = hang(level, fromCauldron, Blocks.LAVA_CAULDRON.defaultBlockState(), Blocks.AIR.defaultBlockState());
				expectPour(CopperSpigotBlock.pour(level, fromCauldron, spigot, null), CopperSpigotBlock.Pour.POURED, "lava cauldron");
				if (!level.getFluidState(fromCauldron.below()).isSourceOfType(Fluids.LAVA)) {
					throw new AssertionError("a lava cauldron poured into air left " + level.getBlockState(fromCauldron.below()));
				}
				if (!level.getBlockState(fromCauldron.above(2)).is(Blocks.CAULDRON)) {
					throw new AssertionError("the lava cauldron was not emptied: " + level.getBlockState(fromCauldron.above(2)));
				}
			});

			// On a wall, facing north: the wall block is south of it and the water south of that.
			// Open air below gets a water source.
			BlockPos wall = origin.south(APART);
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				level.setBlock(wall.south(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(wall.south(2), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
				BlockState spigot = Main.COPPER_SPIGOT_BLOCK.defaultBlockState()
					.setValue(CopperSpigotBlock.HANGING, false)
					.setValue(CopperSpigotBlock.FACING, Direction.NORTH);
				if (!spigot.canSurvive(level, wall)) throw new AssertionError("a wall spigot cannot hold onto stone");
				level.setBlock(wall, spigot, Block.UPDATE_ALL);
				expectPour(CopperSpigotBlock.pour(level, wall, spigot, null), CopperSpigotBlock.Pour.POURED, "wall spigot");
				if (!level.getFluidState(wall.below()).isSourceOfType(Fluids.WATER)) {
					throw new AssertionError("a wall spigot poured into air left " + level.getBlockState(wall.below()));
				}
			});

			// Lava will not go into a cauldron that holds water, and keeps its source when it does not.
			BlockPos mismatched = origin.south(APART).east(APART);
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				BlockState partWater = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 1);
				BlockState spigot = hang(level, mismatched, Blocks.LAVA.defaultBlockState(), partWater);
				expectPour(CopperSpigotBlock.pour(level, mismatched, spigot, null), CopperSpigotBlock.Pour.DRY, "lava over water");
				if (!level.getBlockState(mismatched.below()).equals(partWater)) {
					throw new AssertionError("lava changed a water cauldron to " + level.getBlockState(mismatched.below()));
				}
				if (!level.getFluidState(mismatched.above(2)).isSourceOfType(Fluids.LAVA)) {
					throw new AssertionError("lava was spent on a pour that never happened");
				}
			});

			// Redstone: the rising edge pours once, and a signal held does not pour again.
			BlockPos wired = origin.south(APART).east(2 * APART);
			session.onServer(server -> hang(server.overworld(), wired, Blocks.WATER.defaultBlockState(), Blocks.CAULDRON.defaultBlockState()));
			session.onServer(server -> server.overworld().setBlock(wired.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL));
			session.waitTicks(REDSTONE_POUR);
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				expectFullWaterCauldron(level, wired.below(), "redstone pulse");
				if (!level.getBlockState(wired).getValue(CopperSpigotBlock.POWERED)) {
					throw new AssertionError("a powered spigot does not remember being powered");
				}
				level.setBlock(wired.below(), Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
			});
			session.waitTicks(REDSTONE_POUR);
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				if (!level.getBlockState(wired.below()).is(Blocks.CAULDRON)) {
					throw new AssertionError("a held signal poured again: " + level.getBlockState(wired.below()));
				}
				level.setBlock(wired.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			});
			session.waitTicks(2);
			session.onServer(server -> server.overworld().setBlock(wired.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL));
			session.waitTicks(REDSTONE_POUR);
			session.onServer(server -> expectFullWaterCauldron(server.overworld(), wired.below(), "second redstone pulse"));

			// An empty bucket held to a spigot over lava, within reach: the bucket comes away full of
			// lava, the lava is gone from behind the support, and nothing lands below.
			BlockPos[] near = new BlockPos[1];
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = session.player();
				near[0] = player.blockPosition().above(2).north(2);
				BlockState spigot = hang(level, near[0], Blocks.LAVA.defaultBlockState(), Blocks.AIR.defaultBlockState());
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
				InteractionResult clicked = spigot.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(near[0]), Direction.SOUTH, near[0], false));
				if (!clicked.consumesAction()) throw new AssertionError("an empty bucket on the spigot returned " + clicked);
			});
			session.waitTicks(CopperSpigotBlock.TURN_TICKS + 2);
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = session.player();
				if (!holds(player, Items.LAVA_BUCKET)) {
					throw new AssertionError("the bucket held to the spigot was not filled: hand holds " + player.getMainHandItem());
				}
				if (!level.getBlockState(near[0].above(2)).isAir()) {
					throw new AssertionError("filling a bucket left the lava source behind: " + level.getBlockState(near[0].above(2)));
				}
				if (!level.getBlockState(near[0].below()).isAir()) {
					throw new AssertionError("filling a bucket also poured below: " + level.getBlockState(near[0].below()));
				}
				for (int up = -1; up <= 2; up++) level.setBlock(near[0].above(up), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			});

			// Hung from a waterlogged copper grate with nothing behind it: the grate's own water is
			// drawn, and the grate stays full.
			BlockPos grate = origin.south(2 * APART);
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				BlockState spigot = hang(level, grate, Blocks.AIR.defaultBlockState(), Blocks.CAULDRON.defaultBlockState());
				level.setBlock(grate.above(), Blocks.COPPER_GRATE.weathering().unaffected().defaultBlockState()
					.setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_ALL);
				if (!spigot.canSurvive(level, grate)) throw new AssertionError("a hanging spigot cannot hang from a copper grate");
				expectPour(CopperSpigotBlock.pour(level, grate, spigot, null), CopperSpigotBlock.Pour.POURED, "waterlogged grate");
				expectFullWaterCauldron(level, grate.below(), "hanging from a waterlogged grate");
				if (!level.getFluidState(grate.above()).isSourceOfType(Fluids.WATER)) {
					throw new AssertionError("drawing from a waterlogged grate emptied it: " + level.getBlockState(grate.above()));
				}
			});

			// On the side of a waterlogged grate, with dry stone behind it: the side face draws from the
			// grate just as the bottom face does.
			BlockPos grateWall = origin.south(2 * APART).east(APART);
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				level.setBlock(grateWall.south(), Blocks.COPPER_GRATE.weathering().unaffected().defaultBlockState()
					.setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_ALL);
				level.setBlock(grateWall.south(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(grateWall.below(), Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
				BlockState spigot = Main.COPPER_SPIGOT_BLOCK.defaultBlockState()
					.setValue(CopperSpigotBlock.HANGING, false)
					.setValue(CopperSpigotBlock.FACING, Direction.NORTH);
				if (!spigot.canSurvive(level, grateWall)) throw new AssertionError("a wall spigot cannot hold onto a copper grate");
				level.setBlock(grateWall, spigot, Block.UPDATE_ALL);
				expectPour(CopperSpigotBlock.pour(level, grateWall, spigot, null), CopperSpigotBlock.Pour.POURED,
					"wall spigot on a waterlogged grate");
				expectFullWaterCauldron(level, grateWall.below(), "on the side of a waterlogged grate");
			});

			// Take the support away and the spigot comes off with it.
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				level.setBlock(hungWater.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				if (level.getBlockState(hungWater).is(Main.COPPER_SPIGOT_BLOCK)) {
					throw new AssertionError("a spigot stayed up with nothing to hang from");
				}
			});
		});
	}

	/** A hanging rig: support above the spigot, {@code source} above that, {@code below} beneath it. */
	private static BlockState hang(ServerLevel level, BlockPos pos, BlockState source, BlockState below) {
		level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(pos.above(2), source, Block.UPDATE_ALL);
		level.setBlock(pos.below(), below, Block.UPDATE_ALL);
		BlockState spigot = Main.COPPER_SPIGOT_BLOCK.defaultBlockState().setValue(CopperSpigotBlock.HANGING, true);
		if (!spigot.canSurvive(level, pos)) throw new AssertionError("a hanging spigot cannot hang from stone");
		level.setBlock(pos, spigot, Block.UPDATE_ALL);
		return spigot;
	}

	/** In either hand or anywhere in the inventory: creative keeps the empty bucket and adds the full one. */
	private static boolean holds(ServerPlayer player, net.minecraft.world.item.Item item) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).is(item)) return true;
		}
		return player.getOffhandItem().is(item);
	}

	private static void expectPour(CopperSpigotBlock.Pour got, CopperSpigotBlock.Pour wanted, String what) {
		if (got != wanted) throw new AssertionError(what + ": poured " + got + ", expected " + wanted);
	}

	private static void expectFullWaterCauldron(ServerLevel level, BlockPos pos, String what) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.WATER_CAULDRON) || state.getValue(LayeredCauldronBlock.LEVEL) != LayeredCauldronBlock.MAX_FILL_LEVEL) {
			throw new AssertionError(what + ": the cauldron below is " + state + ", not a full water cauldron");
		}
	}
}
