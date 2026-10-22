package io.swerr.multisession.mixin;

import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RegistryDataCollector.class)
public abstract class RegistryDataCollectorMixin {

    @ModifyVariable(method = "collectGameRegistries", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean multisession$keepGlobalRegistries(boolean synchronizedRegistriesOnly) {
        return synchronizedRegistriesOnly || SessionManager.protectsGlobalRegistries();
    }
}
