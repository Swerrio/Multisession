package io.swerr.multisession.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import io.swerr.multisession.session.SessionManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.Connection;
import org.jspecify.annotations.Nullable;

public final class SessionSnapshots {

    private static final long INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(200);
    private static final int MAX_WIDTH = 480;

    private static final Map<Connection, Snapshot> SNAPSHOTS = new HashMap<>();
    private static final List<Retired> RETIRED = new ArrayList<>();
    private static @Nullable TextureTarget halfSize;
    private static long frame;

    private SessionSnapshots() {
    }

    public static @Nullable Snapshot get(Connection key) {
        return SNAPSHOTS.get(key);
    }

    public static void retainOnly(Set<Connection> keys) {
        Iterator<Map.Entry<Connection, Snapshot>> iterator = SNAPSHOTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Connection, Snapshot> entry = iterator.next();
            if (!keys.contains(entry.getKey())) {
                retire(entry.getValue().target);
                iterator.remove();
            }
        }
    }

    public static void beginFrame() {
        frame++;
        RETIRED.removeIf(retired -> {
            if (retired.frame >= frame) {
                return false;
            }
            retired.target.destroyBuffers();
            return true;
        });
    }

    public static void capture(Minecraft minecraft, RenderTarget mainTarget) {
        ClientPacketListener listener = minecraft.getConnection();
        if (listener == null || minecraft.level == null || minecraft.gui.screen() != null || SessionWarp.active() || SessionManager.inBackground()) {
            return;
        }
        GpuTexture color = mainTarget.getColorTexture();
        GpuTextureView colorView = mainTarget.getColorTextureView();
        if (color == null || colorView == null) {
            return;
        }

        Connection key = listener.getConnection();
        Snapshot snapshot = SNAPSHOTS.get(key);
        long now = System.nanoTime();
        if (snapshot != null && now - snapshot.taken < INTERVAL_NANOS) {
            return;
        }

        int width = color.getWidth(0);
        int height = color.getHeight(0);
        int thumbWidth = Math.min(MAX_WIDTH, width);
        int thumbHeight = Math.max(1, Math.round(thumbWidth * (float) height / width));
        if (snapshot == null || snapshot.target.width != thumbWidth || snapshot.target.height != thumbHeight) {
            if (snapshot != null) {
                retire(snapshot.target);
            }
            snapshot = new Snapshot(new TextureTarget("Multisession snapshot", thumbWidth, thumbHeight, color.getFormat(), null));
            SNAPSHOTS.put(key, snapshot);
        }

        GpuTextureView source = colorView;
        if (width >= thumbWidth * 3) {
            int halfWidth = width / 2;
            int halfHeight = Math.max(1, height / 2);
            if (halfSize == null) {
                halfSize = new TextureTarget("Multisession snapshot half", halfWidth, halfHeight, color.getFormat(), null);
            } else if (halfSize.width != halfWidth || halfSize.height != halfHeight) {
                halfSize.resize(halfWidth, halfHeight);
            }
            blit(colorView, halfSize.getColorTextureView());
            source = halfSize.getColorTextureView();
        }
        blit(source, snapshot.target.getColorTextureView());
        snapshot.taken = now;
        snapshot.aspect = width / (float) height;
    }

    private static void blit(GpuTextureView source, @Nullable GpuTextureView target) {
        if (target == null) {
            return;
        }
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Multisession snapshot", target, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.TRACY_BLIT));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void retire(TextureTarget target) {
        RETIRED.add(new Retired(target, frame));
    }

    public static final class Snapshot {

        private final TextureTarget target;
        private long taken;
        private float aspect = 16.0F / 9.0F;

        private Snapshot(TextureTarget target) {
            this.target = target;
        }

        public @Nullable GpuTextureView view() {
            return target.getColorTextureView();
        }

        public float aspect() {
            return aspect;
        }
    }

    private record Retired(TextureTarget target, long frame) {
    }
}
