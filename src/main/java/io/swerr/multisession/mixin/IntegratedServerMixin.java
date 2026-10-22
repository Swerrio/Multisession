package io.swerr.multisession.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {

    @ModifyExpressionValue(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isPaused()Z"))
    private boolean multisession$pauseWhileParked(boolean paused) {
        return paused || SessionManager.isParkedServer((IntegratedServer) (Object) this);
    }

    @ModifyExpressionValue(
            method = "updateCommandsAllowedForOtherPlayers",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;"))
    private @Nullable LocalPlayer multisession$onlyOwnLocalPlayer(@Nullable LocalPlayer player) {
        if (SessionManager.isParkedServer((IntegratedServer) (Object) this) || SessionManager.inBackground()) {
            return null;
        }
        return player;
    }
}
