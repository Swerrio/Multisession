package io.swerr.multisession.mixin;

import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IntegratedServer.class)
public interface IntegratedServerAccessor {

    @Accessor("paused")
    boolean multisession$paused();
}
