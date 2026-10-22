package io.swerr.multisession.mixin;

import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {

    @Inject(method = "stopPlaying()V", at = @At("HEAD"), cancellable = true)
    private void multisession$keepActiveMusic(CallbackInfo ci) {
        if (SessionManager.inBackground()) {
            ci.cancel();
        }
    }
}
