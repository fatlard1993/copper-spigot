package justfatlard.copper_spigot.gametest;

import justfatlard.copper_spigot.CopperSpigotBlock;
import justfatlard.copper_spigot.Main;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, Main.MOD_ID, session -> {
			// The player is the server's to read, so its position is fetched on the server thread.
			BlockPos[] at = new BlockPos[1];
			session.onServer(server -> at[0] = session.player().blockPosition().above(24));
			BlockPos origin = at[0];

			// Hanging, water above the support, an empty cauldron below: a click fills it to the
			// brim and the water stays where it was.
			BlockPos hungWater = origin;
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				BlockState spigot = hang(level, hungWater, Blocks.WATER.defaultBlockState(), Blocks.CAULDRON.defaultBlockState());
				InteractionResult clicked = spigot.useWithoutItem(level, session.player(),
					new BlockHitResult(Vec3.atCenterOf(hungWater), Direction.NORTH, hungWater, false));
				if (!clicked.consumesAction()) throw new AssertionError("clicking the spigot returned " + clicked);
				expectFullWaterCauldron(level, hungWater.below(), "hanging spigot under water");
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
			session.waitTicks(6);
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				expectFullWaterCauldron(level, wired.below(), "redstone pulse");
				if (!level.getBlockState(wired).getValue(CopperSpigotBlock.POWERED)) {
					throw new AssertionError("a powered spigot does not remember being powered");
				}
				level.setBlock(wired.below(), Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
			});
			session.waitTicks(10);
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				if (!level.getBlockState(wired.below()).is(Blocks.CAULDRON)) {
					throw new AssertionError("a held signal poured again: " + level.getBlockState(wired.below()));
				}
				level.setBlock(wired.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			});
			session.waitTicks(2);
			session.onServer(server -> server.overworld().setBlock(wired.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL));
			session.waitTicks(6);
			session.onServer(server -> expectFullWaterCauldron(server.overworld(), wired.below(), "second redstone pulse"));

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
