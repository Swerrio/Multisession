package io.swerr.multisession.mixin;

import io.swerr.multisession.session.ChunkTrackingReplay;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.client.multiplayer.ClientChunkCache$Storage")
public abstract class ClientChunkCacheStorageMixin implements ChunkTrackingReplay {

    @Shadow
    @Final
    private AtomicReferenceArray<@Nullable LevelChunk> chunks;

    @Shadow
    @Final
    private LongOpenHashSet[] addedEmptySections;

    @Shadow
    @Final
    private LongOpenHashSet[] removedEmptySections;

    @Shadow
    @Final
    private LongOpenHashSet[] addedLoadedChunks;

    @Shadow
    @Final
    private LongOpenHashSet[] removedLoadedChunks;

    @Shadow
    private int updatingSetsIndex;

    @Shadow
    private void onChunkAdded(LevelChunk chunk) {
        throw new AssertionError();
    }

    @Override
    public void multisession$replayLoadedChunks() {
        int index = this.updatingSetsIndex;
        this.addedEmptySections[index].clear();
        this.removedEmptySections[index].clear();
        this.addedLoadedChunks[index].clear();
        this.removedLoadedChunks[index].clear();
        this.multisession$forEachLoadedChunk(this::onChunkAdded);
    }

    @Override
    public void multisession$forEachLoadedChunk(Consumer<LevelChunk> action) {
        for (int i = 0; i < this.chunks.length(); i++) {
            LevelChunk chunk = this.chunks.get(i);
            if (chunk != null) {
                action.accept(chunk);
            }
        }
    }
}
