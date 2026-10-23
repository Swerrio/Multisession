package io.swerr.multisession.session;

import java.util.function.Consumer;
import net.minecraft.world.level.chunk.LevelChunk;

public interface ChunkTrackingReplay {

    void multisession$forEachLoadedChunk(Consumer<LevelChunk> action);
}
