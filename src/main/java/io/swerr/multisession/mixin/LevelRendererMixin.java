package io.swerr.multisession.mixin;

import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(
            method = {
                    "blockChanged(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V",
                    "setBlockDirty(Lnet/minecraft/core/BlockPos;Z)V",
                    "setBlockDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
                    "setBlocksDirty(IIIIII)V",
                    "setSectionDirtyWithNeighbors(III)V",
                    "setSectionDirty(III)V",
                    "setSectionDirty(IIIZ)V"
            },
            at = @At("HEAD"),
            cancellable = true)
    private void multisession$ignoreBackgroundSections(CallbackInfo ci) {
        if (SessionManager.inBackground()) {
            ci.cancel();
        }
    }
}
