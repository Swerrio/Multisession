package io.swerr.multisession.mixin;

import io.swerr.multisession.client.SessionWarp;
import io.swerr.multisession.session.ClientSession;
import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    private volatile boolean pause;

    @Shadow
    private @Nullable IntegratedServer singleplayerServer;

    @Inject(method = "tick", at = @At("TAIL"))
    private void multisession$tickParkedSessions(CallbackInfo ci) {
        SessionManager.tickBackground((Minecraft) (Object) this);
    }

    @Inject(method = "doWorldLoad", at = @At("HEAD"))
    private void multisession$keepOneWorldOpen(CallbackInfo ci) {
        SessionManager.closeParkedWorlds((Minecraft) (Object) this);
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void multisession$advanceSwitchEffect(boolean advanceGameTime, CallbackInfo ci) {
        SessionWarp.frame((Minecraft) (Object) this);
    }

    @Inject(method = "setLevel", at = @At("HEAD"), cancellable = true)
    private void multisession$setBackgroundLevel(ClientLevel level, CallbackInfo ci) {
        if (SessionManager.inBackground()) {
            ((Minecraft) (Object) this).level = level;
            ci.cancel();
        }
    }

    @Inject(method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V", at = @At("HEAD"), cancellable = true)
    private void multisession$keepEnginesOnActiveLevel(ClientLevel level, boolean stopSound, CallbackInfo ci) {
        if (SessionManager.inBackground()) {
            ci.cancel();
        }
    }

    @Inject(method = "setCameraEntity", at = @At("HEAD"), cancellable = true)
    private void multisession$setBackgroundCamera(Entity cameraEntity, CallbackInfo ci) {
        ClientSession session = SessionManager.background();
        if (session != null) {
            session.setCamera(cameraEntity);
            ci.cancel();
        }
    }

    @Inject(method = "getCameraEntity", at = @At("HEAD"), cancellable = true)
    private void multisession$getBackgroundCamera(CallbackInfoReturnable<Entity> cir) {
        ClientSession session = SessionManager.background();
        if (session != null) {
            cir.setReturnValue(session.camera());
        }
    }

    @Inject(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;pause:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void multisession$pauseDuringSwitch(boolean advanceGameTime, CallbackInfo ci) {
        if (!this.pause && SessionWarp.holdsPause() && this.singleplayerServer != null && !this.singleplayerServer.isPublished()) {
            this.pause = true;
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void multisession$deferScreen(@Nullable Screen screen, CallbackInfo ci) {
        ClientSession session = SessionManager.background();
        if (session != null) {
            session.setPendingScreen(screen);
            ((Minecraft) (Object) this).screen = screen;
            ci.cancel();
        }
    }

    @Inject(method = "setScreenAndShow", at = @At("HEAD"), cancellable = true)
    private void multisession$deferScreenAndShow(Screen screen, CallbackInfo ci) {
        ClientSession session = SessionManager.background();
        if (session != null) {
            session.setPendingScreen(screen);
            ci.cancel();
        }
    }

    @Inject(method = "clearClientLevel", at = @At("HEAD"), cancellable = true)
    private void multisession$reconfigureInBackground(Screen screen, CallbackInfo ci) {
        if (SessionManager.inBackground()) {
            SessionManager.onBackgroundReconfigure((Minecraft) (Object) this, screen);
            ci.cancel();
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"), cancellable = true)
    private void multisession$disconnectInBackground(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        if (SessionManager.inBackground()) {
            SessionManager.onBackgroundDisconnect();
            ci.cancel();
        }
    }

    @Inject(method = "clearDownloadedResourcePacks", at = @At("HEAD"), cancellable = true)
    private void multisession$keepActiveResourcePacks(CallbackInfo ci) {
        if (SessionManager.inBackground()) {
            ci.cancel();
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void multisession$closeParkedSessions(CallbackInfo ci) {
        SessionManager.shutdown();
    }
}
