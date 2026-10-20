package justfatlard.copper_spigot.integration;

import justfatlard.block_tip.api.BlockTipApi;
import justfatlard.copper_spigot.CopperSpigotBlock;
import justfatlard.copper_spigot.Main;
import net.minecraft.world.level.material.Fluids;

/**
 * What a spigot would draw, and whether the spot under it can take it.
 *
 * <p>Both are invisible. The source is two blocks away on the far side of a wall or ceiling,
 * which is exactly where a player cannot see it, and a spigot that clicks dry gives no hint
 * whether the trouble is behind it or below it. The card says which, before anybody clicks.
 *
 * <p>Read from the same two functions the pour runs on, so the card cannot promise a pour that
 * the click then refuses.
 */
public final class SpigotTipRegistration {
	private SpigotTipRegistration() {}

	public static void register() {
		BlockTipApi.describe((level, pos, state, player) -> {
			if (!state.is(Main.COPPER_SPIGOT_BLOCK)) return null;

			CopperSpigotBlock.Source source = CopperSpigotBlock.sourceAt(level, CopperSpigotBlock.sourcePos(state, pos));
			if (source == null) return "Nothing to draw";

			String fluid = source.fluid() == Fluids.LAVA ? "Lava" : "Water";
			return fluid + ": " + switch (CopperSpigotBlock.planBelow(level, pos.below(), source.fluid())) {
				case FILL_CAULDRON -> "fills the cauldron";
				case WATERLOG -> "waterlogs below";
				case POUR -> "pours below";
				case EVAPORATE -> "boils away";
				case BLOCKED -> "blocked below";
			};
		});
	}
}
