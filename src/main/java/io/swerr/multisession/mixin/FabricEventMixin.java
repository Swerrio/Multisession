package io.swerr.multisession.mixin;

import io.swerr.multisession.session.SessionManager;
import io.swerr.multisession.session.WorldEvents;
import net.fabricmc.fabric.api.event.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Event.class)
public abstract class FabricEventMixin<T> {

    @Shadow
    protected volatile T invoker;

    @Inject(method = "invoker", at = @At("HEAD"), cancellable = true)
    private void multisession$hideBackgroundWorlds(CallbackInfoReturnable<T> cir) {
        if (SessionManager.inBackground()) {
            @SuppressWarnings("unchecked")
            T silent = (T) WorldEvents.silentInvoker((Event<?>) (Object) this, this.invoker);
            if (silent != null) {
                cir.setReturnValue(silent);
            }
        }
    }
}
