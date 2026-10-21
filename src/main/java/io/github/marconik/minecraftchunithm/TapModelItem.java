package io.github.marconik.minecraftchunithm;

import net.minecraft.core.Direction;
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
public final class TapModelItem extends Item {
    private final int lanes;

    public TapModelItem(Properties properties, int lanes) {
        super(properties);
        this.lanes = lanes;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        // Model placement is a creative-mode preview tool, not a survival mechanic.
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            return InteractionResult.PASS;
        }
        Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display == null) return InteractionResult.FAIL;
        Vec3 hit = context.getClickLocation();
        var state = level.getBlockState(context.getClickedPos());
        Direction direction = state.getBlock() instanceof TrackBlock
                ? state.getValue(TrackBlock.FACING) : context.getHorizontalDirection();
        boolean alongZ = direction.getAxis() == Direction.Axis.X;
        double width = lanes * 0.5;
        // The clicked half-lane is the low-coordinate edge; cap details keep their physical size.
        double x = alongZ ? context.getClickedPos().getX() + 0.5 : Math.floor(hit.x * 2) / 2 + width / 2;
        double z = alongZ ? Math.floor(hit.z * 2) / 2 + width / 2 : context.getClickedPos().getZ() + 0.5;
        display.setPos(x, hit.y + 0.015625, z);
        display.setYRot(alongZ ? 90 : 0);
        display.setItemStack(new ItemStack(this));
        display.setItemTransform(ItemDisplayContext.FIXED);
        display.setBrightnessOverride(new Brightness(15, 15));
        display.setWidth((float) width + 0.5F);
        display.setHeight(0.5F);
        display.addTag("minecraftchunithm.tap_preview");
        level.addFreshEntity(display);
        return InteractionResult.SUCCESS;
    }
}
