package io.swerr.multisession.session;

import com.google.common.hash.Hashing;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;
import org.jspecify.annotations.Nullable;

public final class RegistryBinding {

    private static final Map<ResourceKey<? extends Registry<?>>, Long> BOUND_TAGS = new HashMap<>();
    private static @Nullable Connection componentsOwner;

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

    public static void markComponentsBound(Connection connection) {
        componentsOwner = connection;
    }

    public static void bindTags(ClientPacketListener listener) {
        ConnectionTags record = (ConnectionTags) listener.getConnection();
        record.multisession$fingerprints().forEach((key, fingerprint) -> {
            if (!Objects.equals(BOUND_TAGS.get(key), fingerprint)) {
                Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(key.identifier());
                if (registry != null) {
                    applyTags(registry, record.multisession$tags().get(key));
                    BOUND_TAGS.put(key, fingerprint);
                }
            }
        });
    }

    public static void bindComponents(ClientPacketListener listener) {
        Connection connection = listener.getConnection();
        if (componentsOwner == connection) {
            return;
        }

        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        HolderLookup.Provider registries = connection.isMemoryConnection() && server != null
                ? server.reloadableRegistries().lookup()
                : listener.registryAccess();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(DataComponentInitializers.PendingComponents::apply);
        componentsOwner = connection;
    }

    private static <T> void applyTags(Registry<T> registry, TagNetworkSerialization.NetworkPayload payload) {
        registry.prepareTagReload(payload.resolve(registry)).apply();
    }

    private static long fingerprint(TagNetworkSerialization.NetworkPayload payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            TagNetworkSerialization.NetworkPayload.STREAM_CODEC.encode(buffer, payload);
            return Hashing.murmur3_128().hashBytes(buffer.array(), buffer.arrayOffset(), buffer.writerIndex()).asLong();
        } finally {
            buffer.release();
        }
    }
}
