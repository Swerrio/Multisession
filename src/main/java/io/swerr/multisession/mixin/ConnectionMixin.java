package io.swerr.multisession.mixin;

import io.swerr.multisession.session.ConnectionTags;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements ConnectionTags {

    @Unique
    private final Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> multisession$tags = new HashMap<>();

    @Unique
    private final Map<ResourceKey<? extends Registry<?>>, Long> multisession$fingerprints = new HashMap<>();

    @Override
    public Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> multisession$tags() {
        return this.multisession$tags;
    }

    @Override
    public Map<ResourceKey<? extends Registry<?>>, Long> multisession$fingerprints() {
        return this.multisession$fingerprints;
    }
}
