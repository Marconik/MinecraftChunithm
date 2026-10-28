package io.github.marconik.minecraftchunithm.client.mixin;

import io.github.marconik.minecraftchunithm.client.ChartDisplayInterpolation;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisplayRenderer.class)
abstract class ChartDisplayRendererMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Display;Lnet/minecraft/client/renderer/entity/state/DisplayEntityRenderState;F)V",at=@At("TAIL"))
    private void minecraftchunithm$affinePose(Display entity,DisplayEntityRenderState state,float partialTick,CallbackInfo ci){
        ChartDisplayInterpolation.apply(entity,state,partialTick);
    }
}
