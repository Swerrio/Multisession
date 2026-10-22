package io.swerr.multisession.mixin;

import net.minecraft.network.PacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public interface ListenerAndPacketAccessor {

    @Accessor("listener")
    PacketListener multisession$listener();

    @Invoker("handle")
    void multisession$handle();
}
