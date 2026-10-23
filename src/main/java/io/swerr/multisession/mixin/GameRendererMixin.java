package io.swerr.multisession.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import io.swerr.multisession.client.SessionSnapshots;
import io.swerr.multisession.client.SessionWarp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private RenderTarget mainRenderTarget;

    @Inject(method = "render", at = @At("HEAD"))
    private void multisession$retireSnapshots(CallbackInfo ci) {
        SessionSnapshots.beginFrame();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;applyPostEffects()V", shift = At.Shift.AFTER))
    private void multisession$captureSnapshot(CallbackInfo ci) {
        SessionSnapshots.capture(minecraft, mainRenderTarget);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void multisession$renderSwitchEffect(CallbackInfo ci) {
        SessionWarp.render(minecraft, mainRenderTarget);
    }
}
