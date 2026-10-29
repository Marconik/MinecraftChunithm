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
                    new TrackBlock(CabinetBlocks.properties(blockKey)
                            .sound(SoundType.METAL).noOcclusion(), switch (name) {
                                case "track_left" -> TrackBlock.Part.LEFT;
                                case "track_right" -> TrackBlock.Part.RIGHT;
                                case "track_judgment" -> TrackBlock.Part.JUDGMENT;
                                default -> TrackBlock.Part.DECK;
                            }));
            items.add(Registry.register(BuiltInRegistries.ITEM, itemKey,
                    new BlockItem(block, new Item.Properties().setId(itemKey).fireResistant().useBlockDescriptionPrefix())));
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
        List<String> chartModels=new ArrayList<>(List.of("chart_hold_body","chart_slide_body","chart_air_body"));
        for(int width=1;width<=16;width++)for(String kind:List.of("slide_node","air_arrow","action_bar"))
            chartModels.add("chart_"+kind+"_"+width);
        for(int width=1;width<=16;width++)for(String variant:List.of("up_left","up_right","down","down_left","down_right"))
            chartModels.add("chart_air_arrow_"+variant+"_"+width);
        for(String color:List.of("gry","aqa","red","blk","def","dgr","grn","pnk","cyn","blu","orn","yel","ppl","vlt","lim"))
            chartModels.add("chart_effect_"+color+"_body");
        for(String name:chartModels){
            var key=ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,name));
            Registry.register(BuiltInRegistries.ITEM,key,new Item(new Item.Properties().setId(key)));
        }
        for (var kind : NoteDefinition.Kind.values()) {
            if (kind==NoteDefinition.Kind.TAP) continue;
            for (int lanes=1;lanes<=16;lanes++) {
                if(kind==NoteDefinition.Kind.AIR_ACTION && lanes!=2)continue;
                if(kind==NoteDefinition.Kind.SPEED_CHANGE && lanes!=16)continue;
                String name=kind==NoteDefinition.Kind.SPEED_CHANGE?"speed_change_line":
                        kind.name().toLowerCase(java.util.Locale.ROOT)+"_"+lanes;
                ResourceKey<Item> key=ResourceKey.create(Registries.ITEM,
                        Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,name));
                items.add(Registry.register(BuiltInRegistries.ITEM,key,
                        new NoteModelItem(new Item.Properties().setId(key).stacksTo(1),lanes,kind)));
            }
        }
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
                .register(output -> items.forEach(output::accept));
        for (var kind : List.of(NoteDefinition.Kind.SLIDE,NoteDefinition.Kind.AIR_ACTION,NoteDefinition.Kind.AIR)) {
            var variants=kind==NoteDefinition.Kind.AIR
                    ?List.of("up_left","up_right","down","down_left","down_right")
                    :List.of("parabola_up","parabola_down");
            for (String variant:variants) for(int width=1;width<=16;width++) {
                if(kind==NoteDefinition.Kind.AIR_ACTION && width!=2)continue;
                String name=kind.name().toLowerCase(java.util.Locale.ROOT)+"_"+variant+"_"+width;
                var key=ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,name));
                items.add(Registry.register(BuiltInRegistries.ITEM,key,new NoteModelItem(
                        new Item.Properties().setId(key).stacksTo(1),width,kind,
                        kind==NoteDefinition.Kind.AIR?NoteDefinition.Direction.valueOf(variant.toUpperCase(java.util.Locale.ROOT)):NoteDefinition.Direction.UP,
                        kind==NoteDefinition.Kind.AIR?NoteDefinition.Connection.LINEAR:
                                NoteDefinition.Connection.valueOf(variant.toUpperCase(java.util.Locale.ROOT)))));
            }
        }
    }
}
