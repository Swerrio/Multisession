package io.swerr.multisession.session;

import io.swerr.multisession.mixin.ClientNetworkingImplAccessor;
import net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon;
import org.jspecify.annotations.Nullable;

public final class FabricAddons {

    public static final Object LOCK = new Object();

    private static boolean swapping;
    private static boolean playDeferred;
    private static @Nullable ClientPlayNetworkAddon deferredPlay;

    private FabricAddons() {
    }

    public static void setPlayFromOtherThread(@Nullable ClientPlayNetworkAddon addon) {
        synchronized (LOCK) {
            if (swapping) {
                playDeferred = true;
                deferredPlay = addon;
            } else {
                ClientNetworkingImplAccessor.multisession$setPlayAddon(addon);
            }
        }
    }

    static void beginSwap() {
        swapping = true;
    }

    static void endSwap() {
        if (playDeferred) {
            ClientNetworkingImplAccessor.multisession$setPlayAddon(deferredPlay);
        }
        playDeferred = false;
        deferredPlay = null;
        swapping = false;
    }
}
