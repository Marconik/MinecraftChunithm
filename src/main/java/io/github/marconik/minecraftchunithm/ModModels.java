package io.github.marconik.minecraftchunithm;

import java.util.ArrayList;
import java.util.List;
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

public final class ModModels {
    private ModModels() {}

    public static void initialize() {
        List<Item> items = new ArrayList<>();
        for (String name : List.of("track", "track_left", "track_right", "track_judgment")) {
            Identifier id = Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID, name);
            ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
            Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
                    new TrackBlock(BlockBehaviour.Properties.of().setId(blockKey)
                            .strength(1.5F).sound(SoundType.METAL).noOcclusion(), switch (name) {
                                case "track_left" -> TrackBlock.Part.LEFT;
                                case "track_right" -> TrackBlock.Part.RIGHT;
                                case "track_judgment" -> TrackBlock.Part.JUDGMENT;
                                default -> TrackBlock.Part.DECK;
                            }));
            items.add(Registry.register(BuiltInRegistries.ITEM, itemKey,
                    new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())));
        }
        for (int lanes = 1; lanes <= 16; lanes++) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                    Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID, "tap_" + lanes));
            items.add(Registry.register(BuiltInRegistries.ITEM, key,
                    new TapModelItem(new Item.Properties().setId(key).stacksTo(1), lanes)));
        }
        for (String name : List.of("beat_line", "judgment_line")) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                    Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID, name));
            Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
        }
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
                .register(output -> items.forEach(output::accept));
    }
}
