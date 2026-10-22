package io.swerr.multisession.mixin;

import io.swerr.multisession.session.ClientSession;
import io.swerr.multisession.session.SessionManager;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketProcessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketProcessor.class)
public abstract class PacketProcessorMixin {

    @Shadow
    @Final
    private Queue<?> packetsToBeHandled;

    @Shadow
    private boolean closed;

    @Inject(method = "processQueuedPackets", at = @At("HEAD"), cancellable = true)
    private void multisession$deferParkedPackets(CallbackInfo ci) {
        if (this.closed || !SessionManager.hasParked() || (Object) this != Minecraft.getInstance().packetProcessor()) {
            return;
        }

        Object entry;
        while ((entry = this.packetsToBeHandled.poll()) != null) {
            ListenerAndPacketAccessor task = (ListenerAndPacketAccessor) entry;
            ClientSession session = SessionManager.parkedFor(task.multisession$listener());
            if (session != null) {
                session.defer(task);
            } else {
                task.multisession$handle();
            }
        }
        ci.cancel();
    }
}
