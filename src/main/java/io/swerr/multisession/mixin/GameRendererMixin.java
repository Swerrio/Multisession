package io.swerr.multisession.mixin;

import io.swerr.multisession.client.SessionWarp;
import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void multisession$muteBackgroundItemActivation(CallbackInfo ci) {
        if (SessionManager.inBackground()) {
            ci.cancel();
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void multisession$switchEffectFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        if (useFovSetting) {
            cir.setReturnValue(SessionWarp.fov(minecraft, cir.getReturnValueF()));
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void multisession$renderSwitchEffect(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        SessionWarp.render(minecraft, minecraft.getMainRenderTarget());
    }
}
