package io.swerr.multisession.mixin;

import io.swerr.multisession.session.SessionManager;
import io.swerr.multisession.session.SodiumChunks;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class SodiumWorldRendererMixin {

    @Inject(method = {"onChunkAdded", "onChunkLightAdded", "onChunkRemoved"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void multisession$ignoreBackgroundChunks(int x, int z, CallbackInfo ci) {
        if (SessionManager.inBackground()) {
            ci.cancel();
        }
    }

    @Inject(method = "setWorld", at = @At("TAIL"), require = 0)
    private void multisession$replayLoadedChunks(@Nullable ClientLevel level, CallbackInfo ci) {
        if (level != null) {
            SodiumChunks.replay(this, level);
        }
    }
}
