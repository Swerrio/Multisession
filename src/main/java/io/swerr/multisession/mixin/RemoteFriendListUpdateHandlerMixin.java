package io.swerr.multisession.mixin;

import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RemoteFriendListUpdateHandler.class)
public abstract class RemoteFriendListUpdateHandlerMixin {

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void multisession$keepParkedAccountAlive(CallbackInfo ci) {
        RemoteFriendListUpdateHandler self = (RemoteFriendListUpdateHandler) (Object) this;
        if (SessionManager.usesFriendList(self)) {
            self.stop();
            ci.cancel();
        }
    }
}
