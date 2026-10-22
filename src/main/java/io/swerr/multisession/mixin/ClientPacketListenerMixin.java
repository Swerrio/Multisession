package io.swerr.multisession.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.swerr.multisession.session.RegistryBinding;
import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(
            method = "handleUpdateTags",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER))
    private void multisession$recordTags(ClientboundUpdateTagsPacket packet, CallbackInfo ci) {
        RegistryBinding.recordTags(((ClientPacketListener) (Object) this).getConnection(), packet.tags());
    }

    @ModifyExpressionValue(
            method = "handleUpdateTags",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;isMemoryConnection()Z"))
    private boolean multisession$keepGlobalTags(boolean memoryConnection) {
        return memoryConnection || SessionManager.protectsGlobalRegistries();
    }

    @Inject(method = "handleUpdateTags", at = @At("TAIL"))
    private void multisession$markTagsBound(ClientboundUpdateTagsPacket packet, CallbackInfo ci) {
        if (!SessionManager.protectsGlobalRegistries()) {
            RegistryBinding.markTagsBound(((ClientPacketListener) (Object) this).getConnection());
        }
    }
}
