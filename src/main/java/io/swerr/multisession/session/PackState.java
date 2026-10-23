package io.swerr.multisession.session;

import net.minecraft.server.packs.repository.Pack;
import org.jspecify.annotations.Nullable;

public final class PackState {

    final @Nullable Pack serverPack;

    public PackState(@Nullable Pack serverPack) {
        this.serverPack = serverPack;
    }

    public @Nullable Pack serverPack() {
        return serverPack;
    }
}
