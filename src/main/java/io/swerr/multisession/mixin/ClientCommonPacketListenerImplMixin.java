package io.swerr.multisession.mixin;

import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {

    @Shadow
    @Final
    protected Connection connection;

    @Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
    private void multisession$keepActiveSession(DisconnectionDetails details, CallbackInfo ci) {
        if (SessionManager.parkedFor(this.connection) == null) {
            return;
        }

        SessionManager.onParkedDisconnect(this.connection, details.reason());
        if ((Object) this instanceof ClientPacketListener listener) {
            listener.close();
        }
        ci.cancel();
    }

    @Inject(
            method = "handleTransfer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
                    shift = At.Shift.AFTER),
            cancellable = true)
    private void multisession$refuseBackgroundTransfer(ClientboundTransferPacket packet, CallbackInfo ci) {
        if (SessionManager.parkedFor(this.connection) == null) {
            return;
        }

        this.connection.disconnect(Component.translatable("multisession.disconnect.transfer"));
        this.connection.handleDisconnection();
        ci.cancel();
    }
}
