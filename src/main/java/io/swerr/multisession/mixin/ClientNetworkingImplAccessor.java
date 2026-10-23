package io.swerr.multisession.mixin;

import net.fabricmc.fabric.impl.networking.client.ClientNetworkingImpl;
import net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientNetworkingImpl.class)
public interface ClientNetworkingImplAccessor {

    @Accessor("currentPlayAddon")
    static void multisession$setPlayAddon(@Nullable ClientPlayNetworkAddon addon) {
        throw new AssertionError();
    }
}
