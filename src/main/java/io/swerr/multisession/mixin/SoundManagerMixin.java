package io.swerr.multisession.mixin;

import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void multisession$muteBackgroundSound(CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (SessionManager.inBackground()) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }

    @Inject(
            method = {
                    "playDelayed",
                    "queueTickingSound",
                    "stop(Lnet/minecraft/resources/Identifier;Lnet/minecraft/sounds/SoundSource;)V"
            },
            at = @At("HEAD"),
            cancellable = true)
    private void multisession$muteBackgroundSoundControl(CallbackInfo ci) {
        if (SessionManager.inBackground()) {
            ci.cancel();
        }
    }
}
