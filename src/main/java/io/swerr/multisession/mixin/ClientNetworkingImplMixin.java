package io.swerr.multisession.mixin;

import io.swerr.multisession.session.FabricAddons;
import net.fabricmc.fabric.impl.networking.client.ClientNetworkingImpl;
import net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientNetworkingImpl.class)
public abstract class ClientNetworkingImplMixin {

    @Inject(method = "setClientPlayAddon", at = @At("HEAD"), cancellable = true)
    private static void multisession$guardPlayAddon(ClientPlayNetworkAddon addon, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) {
            FabricAddons.setPlayFromOtherThread(addon);
            ci.cancel();
        }
    }
}
