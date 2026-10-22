package io.swerr.multisession.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.swerr.multisession.client.SessionWarp;
import io.swerr.multisession.session.ClientSession;
import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void multisession$deferScreen(Screen screen, CallbackInfo ci) {
        ClientSession session = SessionManager.background();
        if (session != null) {
            session.setPendingScreen(screen);
            ci.cancel();
        }
    }

    @Inject(method = "screen", at = @At("HEAD"), cancellable = true)
    private void multisession$backgroundScreen(CallbackInfoReturnable<Screen> cir) {
        ClientSession session = SessionManager.background();
        if (session != null) {
            cir.setReturnValue(session.pendingScreen());
        }
    }

    @ModifyReturnValue(method = "isPausing", at = @At("RETURN"))
    private boolean multisession$pauseDuringSwitch(boolean pausing) {
        return pausing || SessionWarp.holdsPause();
    }
}
