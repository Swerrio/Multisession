package io.swerr.multisession.mixin.compat;

import io.swerr.multisession.session.SessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.lambdaurora.lambdynlights.LambDynLights")
public abstract class LambDynLightsMixin {

    @Inject(
            method = {
                    "onStartLevelTick()V",
                    "onEndLevelTick()V",
                    "addLightSource(Ldev/lambdaurora/lambdynlights/engine/source/DynamicLightSource;)V"
            },
            at = @At("HEAD"),
            cancellable = true)
    private void multisession$ignoreBackgroundWorld(CallbackInfo ci) {
        if (SessionManager.inBackground()) {
            ci.cancel();
        }
    }

    @Inject(
            method = {
                    "shouldTick(Ldev/lambdaurora/lambdynlights/engine/source/EntityDynamicLightSource;)Z",
                    "shouldTick(Ldev/lambdaurora/lambdynlights/engine/source/DeferredDynamicLightSource;)Z"
            },
            at = @At("HEAD"),
            cancellable = true)
    private void multisession$skipBackgroundLightSources(CallbackInfoReturnable<Boolean> cir) {
        if (SessionManager.inBackground()) {
            cir.setReturnValue(false);
        }
    }
}
