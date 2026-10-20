package justfatlard.copper_spigot;

import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class Main implements ModInitializer {
	public static final String MOD_ID = "copper-spigot-justfatlard";

	public static final Identifier COPPER_SPIGOT_ID = Identifier.fromNamespaceAndPath(MOD_ID, "copper_spigot");

	public static final ResourceKey<Block> COPPER_SPIGOT_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, COPPER_SPIGOT_ID);
	public static final ResourceKey<Item> COPPER_SPIGOT_ITEM_KEY = ResourceKey.create(Registries.ITEM, COPPER_SPIGOT_ID);

	// A lightning rod's settings: the same three ingots of copper, worked as thin. Solid on
	// purpose, as the rod is, so the water it pours does not wash it off the wall.
	public static final CopperSpigotBlock COPPER_SPIGOT_BLOCK = new CopperSpigotBlock(
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_ORANGE)
			.forceSolidOn()
			.requiresCorrectToolForDrops()
			.strength(3.0F, 6.0F)
			.sound(SoundType.COPPER)
			.noOcclusion()
			.setId(COPPER_SPIGOT_BLOCK_KEY)
	);

	public static final BlockItem COPPER_SPIGOT_ITEM = new BlockItem(
		COPPER_SPIGOT_BLOCK,
		new Item.Properties().setId(COPPER_SPIGOT_ITEM_KEY).useBlockDescriptionPrefix()
	);

	@Override
	public void onInitialize() {
		if (PandoricalApi.isAvailable()) {
			PandoricalApi.content().registerBlock(MOD_ID + ":copper_spigot", new BlockRegistration()
				// Copper's sound and hardness for the client's stand-in; its shape and states
				// come from this block, so the copper block's full cube never shows.
				.baseBlock("minecraft:copper_block")
				// A right-click turns the tap, so the client must not predict a placement.
				.interactive()
				.model(MOD_ID + ":block/copper_spigot_wall"));
			PandoricalApi.content().registerItem(MOD_ID + ":copper_spigot", new ItemRegistration()
				.model(MOD_ID + ":item/copper_spigot"));
			PandoricalApi.content().registerModAssets(MOD_ID);
		}

		Registry.register(BuiltInRegistries.BLOCK, COPPER_SPIGOT_ID, COPPER_SPIGOT_BLOCK);
		Registry.register(BuiltInRegistries.ITEM, COPPER_SPIGOT_ID, COPPER_SPIGOT_ITEM);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries ->
			entries.accept(COPPER_SPIGOT_ITEM));

		System.out.println("[" + MOD_ID + "] Loaded (server-side with Pandorical)");
	}
}
