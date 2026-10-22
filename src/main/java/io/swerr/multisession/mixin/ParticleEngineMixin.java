package io.swerr.multisession.mixin;

import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void multisession$dropBackgroundParticle(CallbackInfoReturnable<Particle> cir) {
        if (SessionManager.inBackground()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(
            method = {
                    "add",
                    "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V",
                    "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V"
            },
            at = @At("HEAD"),
            cancellable = true)
    private void multisession$dropBackgroundParticles(CallbackInfo ci) {
        if (SessionManager.inBackground()) {
            ci.cancel();
        }
    }
}
