package io.swerr.multisession.mixin;

import io.swerr.multisession.session.ChunkTrackingReplay;
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
