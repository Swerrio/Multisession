package io.swerr.multisession.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.swerr.multisession.session.SessionManager;
import java.util.function.BooleanSupplier;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {

    @Shadow
    private boolean paused;

    @Shadow
    private void tickPaused() {
        throw new AssertionError();
    }

    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void multisession$holdParkedWorld(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        if (this.paused && SessionManager.isParkedServer((IntegratedServer) (Object) this)) {
            this.tickPaused();
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isPaused()Z"))
    private boolean multisession$pauseWhileParked(boolean paused) {
        return paused || SessionManager.isParkedServer((IntegratedServer) (Object) this);
    }
}
