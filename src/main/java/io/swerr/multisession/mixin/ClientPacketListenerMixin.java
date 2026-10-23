package io.swerr.multisession.mixin;

import io.swerr.multisession.session.ClientSession;
import io.swerr.multisession.session.DeferredPacket;
import io.swerr.multisession.session.RegistryBinding;
import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Registry;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Shadow
    @Final
    private Connection connection;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
    private void multisession$keepActiveSession(Component reason, CallbackInfo ci) {
        if (SessionManager.parkedFor(this.connection) == null) {
            return;
        }

        SessionManager.onParkedDisconnect(this.connection, reason);
        ci.cancel();
    }

    @Inject(method = "handleResourcePack", at = @At("HEAD"), cancellable = true)
    private void multisession$routePackRequest(ClientboundResourcePackPacket packet, CallbackInfo ci) {
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        if (!this.minecraft.isSameThread()) {
            this.minecraft.execute(new DeferredPacket(self, () -> self.handleResourcePack(packet)));
            ci.cancel();
            return;
        }
        ClientSession session = SessionManager.background();
        if (session != null) {
            session.deferPackRequest(packet);
            ci.cancel();
        }
    }

    @Inject(
            method = "handleUpdateTags",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
                    shift = At.Shift.AFTER))
    private void multisession$recordTags(ClientboundUpdateTagsPacket packet, CallbackInfo ci) {
        RegistryBinding.recordTags(this.connection, packet.getTags());
    }

    @Inject(method = "updateTagsForRegistry", at = @At("HEAD"), cancellable = true)
    private void multisession$keepGlobalTags(ResourceKey<? extends Registry<?>> key, TagNetworkSerialization.NetworkPayload payload, CallbackInfo ci) {
        if (SessionManager.protectsGlobalRegistries() && Registry.REGISTRY.containsKey(key.location())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUpdateTags", at = @At("TAIL"))
    private void multisession$markTagsBound(ClientboundUpdateTagsPacket packet, CallbackInfo ci) {
        if (!SessionManager.protectsGlobalRegistries()) {
            RegistryBinding.markTagsBound(this.connection);
        }
    }
}
