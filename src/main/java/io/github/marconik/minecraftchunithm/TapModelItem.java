package io.github.marconik.minecraftchunithm;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** Static model placement for inspection; playback will use a separate renderer. */
public class TapModelItem extends Item {
    private final int lanes;

    public TapModelItem(Properties properties, int lanes) {
        super(properties);
        this.lanes = lanes;
    }
    protected double previewFootprintWidth() { return lanes*0.5; }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        // Model placement is a creative-mode preview tool, not a survival mechanic.
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            return InteractionResult.PASS;
        }
        Vec3 hit = context.getClickLocation();
        TrackBounds bounds = TrackBounds.find(level, context.getClickedPos());
        if (bounds == null) {
            context.getPlayer().sendOverlayMessage(Component.literal("请在完整的 9 格宽轨道表面放置音符。"));
            return InteractionResult.FAIL;
        }
        boolean alongZ = bounds.alongZ();
        double width = previewFootprintWidth();
        double start = Math.floor((alongZ ? hit.z : hit.x) * 2) / 2;
        double surface = context.getClickedPos().getY() + TrackBlock.SURFACE_HEIGHT;
        if (Math.abs(hit.y - surface) > 0.001 || !bounds.contains(start, width)) {
            context.getPlayer().sendOverlayMessage(Component.literal("音符不能放在金色边框上或超出有效轨道宽度。"));
            return InteractionResult.FAIL;
        }
        Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display == null) return InteractionResult.FAIL;
        // The clicked half-lane is the low-coordinate edge; cap details keep their physical size.
        double x = alongZ ? context.getClickedPos().getX() + 0.5 : Math.floor(hit.x * 2) / 2 + width / 2;
        double z = alongZ ? Math.floor(hit.z * 2) / 2 + width / 2 : context.getClickedPos().getZ() + 0.5;
        display.setPos(x, surface + 0.015625, z);
        display.setYRot(alongZ ? 90 : 0);
        display.setItemStack(new ItemStack(this));
        display.setItemTransform(ItemDisplayContext.FIXED);
        display.setBrightnessOverride(new Brightness(15, 15));
        display.setWidth((float) width + 0.5F);
        display.setHeight(2.5F);
        display.addTag("minecraftchunithm.tap_preview");
        level.addFreshEntity(display);
        return InteractionResult.SUCCESS;
    }
}
