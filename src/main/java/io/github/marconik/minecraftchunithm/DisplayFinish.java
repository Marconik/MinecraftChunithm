package io.github.marconik.minecraftchunithm;

import com.mojang.math.Transformation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

/** Transparent polished coating, independent of the underlying artwork or fill. */
public final class DisplayFinish {
    private DisplayFinish(){}

    public static void polish(ServerLevel level,Placement p,String ownerTag,double x,double y,
                              double width,double height,double z,boolean slanted,boolean meter) {
        var entity=EntityTypes.ITEM_DISPLAY.create(level,EntitySpawnReason.TRIGGERED);
        if(entity==null)throw new IllegalStateException("Cannot create display finish");
        var stack=new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL,Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID,
                "finishes/"+(meter?"meter_polish":"portrait_polish")));
        entity.setItemStack(stack);entity.setItemTransform(ItemDisplayContext.FIXED);
        float shear=slanted?(float)(height*.4):0;
        entity.setTransformation(new Transformation(new Matrix4f().m00((float)width)
                .m11((float)height).m10(shear).m22(.001f*FacadeSurface.DEPTH_SCALE)));
        // The explicit SOUTH quad faces like block displays, not generated sprites.
        // Account for the shear at the centre so it exactly covers the parallelogram.
        var pos=p.point(x+width/2+shear/2,y+height/2,FacadeSurface.depth(z));
        entity.setPos(pos.x,pos.y,pos.z);entity.setYRot(p.yaw());
        entity.setBrightnessOverride(new Brightness(15,15));
        entity.setWidth((float)(width+Math.abs(shear)+.1));entity.setHeight((float)(height+.1));
        entity.addTag(ownerTag);level.addFreshEntity(entity);
    }
}
