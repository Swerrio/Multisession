package io.swerr.multisession.session;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public final class WorldEvents {

    private static final Set<Event<?>> SILENCED_IN_BACKGROUND = Set.of(
            ClientTickEvents.START_LEVEL_TICK,
            ClientTickEvents.END_LEVEL_TICK,
            ClientChunkEvents.CHUNK_LOAD,
            ClientChunkEvents.CHUNK_UNLOAD,
            ClientEntityEvents.ENTITY_LOAD,
            ClientEntityEvents.ENTITY_UNLOAD,
            ClientBlockEntityEvents.BLOCK_ENTITY_LOAD,
            ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD,
            ClientPlayConnectionEvents.JOIN,
            ClientPlayConnectionEvents.DISCONNECT);
    private static final Map<Event<?>, Object> SILENT_INVOKERS = new IdentityHashMap<>();
    private static final Field CHUNK_STORAGE = chunkStorageField();

    private WorldEvents() {
    }

    public static @Nullable Object silentInvoker(Event<?> event, Object invoker) {
        if (!SILENCED_IN_BACKGROUND.contains(event)) {
            return null;
        }
        return SILENT_INVOKERS.computeIfAbsent(event, ignored -> {
            Class<?> listenerType = invoker.getClass().getInterfaces()[0];
            return Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[] {listenerType}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> "SilentInvoker[" + listenerType.getName() + "]";
                    };
                }
                return null;
            });
        });
    }

    static void announceUnloaded(ClientLevel level) {
        for (Entity entity : level.entitiesForRendering()) {
            ClientEntityEvents.ENTITY_UNLOAD.invoker().onUnload(entity, level);
        }
        loadedChunks(level).multisession$forEachLoadedChunk(chunk -> {
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload(blockEntity, level);
            }
            ClientChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(level, chunk);
        });
    }

    static void announceLoaded(ClientLevel level) {
        loadedChunks(level).multisession$forEachLoadedChunk(chunk -> {
            ClientChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, chunk);
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.invoker().onLoad(blockEntity, level);
            }
        });
        for (Entity entity : level.entitiesForRendering()) {
            ClientEntityEvents.ENTITY_LOAD.invoker().onLoad(entity, level);
        }
    }

    static ChunkTrackingReplay loadedChunks(ClientLevel level) {
        try {
            return (ChunkTrackingReplay) CHUNK_STORAGE.get(level.getChunkSource());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot reach the client chunk storage", e);
        }
    }

    private static Field chunkStorageField() {
        try {
            Field field = ClientChunkCache.class.getDeclaredField("storage");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("ClientChunkCache.storage is missing", e);
        }
    }
}
