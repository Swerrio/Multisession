package io.swerr.multisession.mixin;

import io.swerr.multisession.session.RegistryBinding;
import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class ClientConfigurationPacketListenerImplMixin {

    @Inject(
            method = "handleUpdateTags",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER))
    private void multisession$recordTags(ClientboundUpdateTagsPacket packet, CallbackInfo ci) {
        RegistryBinding.recordTags(this.multisession$connection(), packet.tags());
    }

    @Inject(method = "handleConfigurationFinished", at = @At("TAIL"))
    private void multisession$markRegistriesBound(ClientboundFinishConfigurationPacket packet, CallbackInfo ci) {
        if (!SessionManager.protectsGlobalRegistries()) {
            RegistryBinding.markTagsBound(this.multisession$connection());
            RegistryBinding.markComponentsBound(this.multisession$connection());
        }
    }

    @Unique
    private Connection multisession$connection() {
        return ((ClientCommonPacketListenerImplAccessor) this).multisession$connection();
    }
}
