package io.swerr.multisession.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.swerr.multisession.session.DeferredPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PacketUtils.class)
public abstract class PacketUtilsMixin {

    @WrapOperation(
            method = "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/thread/BlockableEventLoop;executeIfPossible(Ljava/lang/Runnable;)V"))
    private static void multisession$deferParkedPackets(
            BlockableEventLoop<?> loop, Runnable task, Operation<Void> original, @Local(argsOnly = true) PacketListener listener) {
        original.call(loop, loop == Minecraft.getInstance() ? new DeferredPacket(listener, task) : task);
    }
}
