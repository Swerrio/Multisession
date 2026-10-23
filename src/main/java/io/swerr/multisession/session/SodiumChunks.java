package io.swerr.multisession.session;

import java.lang.reflect.Method;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public final class SodiumChunks {

    private static boolean resolved;
    private static @Nullable Method chunkAdded;
    private static @Nullable Method chunkLightAdded;

    private SodiumChunks() {
    }

    public static void replay(Object renderer, ClientLevel level) {
        if (!resolved) {
            resolved = true;
            try {
                chunkAdded = renderer.getClass().getMethod("onChunkAdded", int.class, int.class);
                chunkLightAdded = renderer.getClass().getMethod("onChunkLightAdded", int.class, int.class);
            } catch (NoSuchMethodException e) {
                chunkAdded = null;
                chunkLightAdded = null;
            }
        }
        Method added = chunkAdded;
        Method light = chunkLightAdded;
        if (added == null || light == null) {
            return;
        }
        WorldEvents.loadedChunks(level).multisession$forEachLoadedChunk(chunk -> {
            ChunkPos pos = chunk.getPos();
            try {
                added.invoke(renderer, pos.x, pos.z);
                light.invoke(renderer, pos.x, pos.z);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot hand the loaded chunks to Sodium", e);
            }
        });
    }
}
