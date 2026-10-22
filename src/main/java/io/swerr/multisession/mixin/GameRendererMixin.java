package io.swerr.multisession.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import io.swerr.multisession.client.SessionWarp;
import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.DeltaTracker;
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

    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void multisession$muteBackgroundItemActivation(CallbackInfo ci) {
        if (SessionManager.inBackground()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void multisession$renderSwitchEffect(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        SessionWarp.render(minecraft, mainRenderTarget);
    }
}
