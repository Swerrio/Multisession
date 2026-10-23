package io.swerr.multisession.session;

import com.google.common.hash.Hashing;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;

public final class RegistryBinding {

    private static final Map<ResourceKey<? extends Registry<?>>, Long> BOUND_TAGS = new HashMap<>();

    private RegistryBinding() {
    }

    public static void recordTags(Connection connection, Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags) {
        ConnectionTags record = (ConnectionTags) connection;
        tags.forEach((key, payload) -> {
            if (!RegistrySynchronization.isNetworkable(key)) {
                record.multisession$tags().put(key, payload);
                record.multisession$fingerprints().put(key, fingerprint(payload));
            }
        });
    }

    public static void markTagsBound(Connection connection) {
        BOUND_TAGS.putAll(((ConnectionTags) connection).multisession$fingerprints());
    }

    public static void bindTags(ClientPacketListener listener) {
        ConnectionTags record = (ConnectionTags) listener.getConnection();
        record.multisession$fingerprints().forEach((key, fingerprint) -> {
            if (!Objects.equals(BOUND_TAGS.get(key), fingerprint)) {
                Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(key.location());
                if (registry != null) {
                    applyTags(registry, record.multisession$tags().get(key));
                    BOUND_TAGS.put(key, fingerprint);
                }
            }
        });
    }

    private static <T> void applyTags(Registry<T> registry, TagNetworkSerialization.NetworkPayload payload) {
        registry.prepareTagReload(payload.resolve(registry)).apply();
    }

    private static long fingerprint(TagNetworkSerialization.NetworkPayload payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            payload.write(buffer);
            return Hashing.murmur3_128().hashBytes(buffer.array(), buffer.arrayOffset(), buffer.writerIndex()).asLong();
        } finally {
            buffer.release();
        }
    }
}
