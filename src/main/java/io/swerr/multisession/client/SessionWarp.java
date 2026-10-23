package io.swerr.multisession.client;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import io.swerr.multisession.session.ClientSession;
import io.swerr.multisession.session.SessionManager;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class SessionWarp {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long LAUNCH_NANOS = TimeUnit.MILLISECONDS.toNanos(450);
    private static final long ARRIVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(700);
    private static final long NOT_STARTED = Long.MIN_VALUE;
    private static final float PEAK_FOV = 1.6F;
    private static final float MAX_FOV = 150.0F;
    private static final SoundEvent LAUNCH_SOUND = SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath(MultisessionClient.MOD_ID, "warp.launch"));
    private static final SoundEvent ARRIVAL_SOUND = SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath(MultisessionClient.MOD_ID, "warp.arrival"));
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec2().putFloat().putFloat().putFloat().putFloat().putFloat().align(16).get();

    private static LongSupplier clock = System::nanoTime;
    private static Phase phase = Phase.IDLE;
    private static @Nullable ClientSession target;
    private static long phaseStart;
    private static long lastFrame;
    private static float travel;
    private static float warp;
    private static float flash;
    private static float fov = 1.0F;
    private static @Nullable RenderPipeline pipeline;
    private static @Nullable TextureTarget sceneCopy;
    private static @Nullable MappableRingBuffer uniforms;
    private static boolean pipelineFailed;

    private SessionWarp() {
    }

    public static boolean active() {
        return phase != Phase.IDLE;
    }

    public static boolean holdsPause() {
        return phase == Phase.LAUNCH;
    }

    public static void cycle(Minecraft minecraft) {
        ClientSession next = SessionCards.next(minecraft);
        if (next != null) {
            switchTo(minecraft, next);
        }
    }

    public static void switchTo(Minecraft minecraft, ClientSession session) {
        if (phase != Phase.IDLE) {
            return;
        }
        boolean inWorld = minecraft.level != null && minecraft.player != null && SessionManager.canSwitch(minecraft);
        if (!inWorld || !MultisessionConfig.get(MultisessionConfig.Option.WARP_ANIMATION)) {
            SessionManager.activate(minecraft, session);
            if (inWorld) {
                playSound(minecraft, ARRIVAL_SOUND);
            }
            return;
        }

        target = session;
        phase = Phase.LAUNCH;
        phaseStart = clock.getAsLong();
        lastFrame = phaseStart;
        travel = 0.0F;
        launch(0.0F);
        minecraft.gui.setScreen(null);
        playSound(minecraft, LAUNCH_SOUND);
    }

    public static void frame(Minecraft minecraft) {
        if (phase == Phase.IDLE) {
            return;
        }

        long now = clock.getAsLong();
        if (phase == Phase.LAUNCH && now - phaseStart >= LAUNCH_NANOS) {
            ClientSession session = target;
            target = null;
            phase = Phase.ARRIVAL;
            phaseStart = NOT_STARTED;
            arrival(0.0F);
            SessionManager.activate(minecraft, session);
            playSound(minecraft, ARRIVAL_SOUND);
            return;
        }
        if (phaseStart == NOT_STARTED) {
            phaseStart = now;
            lastFrame = now;
        }

        float progress = (now - phaseStart) / (float) (phase == Phase.LAUNCH ? LAUNCH_NANOS : ARRIVAL_NANOS);
        if (phase == Phase.ARRIVAL && progress >= 1.0F) {
            phase = Phase.IDLE;
            fov = 1.0F;
            return;
        }
        if (phase == Phase.LAUNCH) {
            launch(progress);
        } else {
            arrival(progress);
        }
        travel += Math.min((now - lastFrame) / 1.0e9F, 0.1F) * (0.25F + 4.0F * warp * warp);
        lastFrame = now;
    }

    public static float fov(Minecraft minecraft, float baseFov) {
        if (phase == Phase.IDLE) {
            return baseFov;
        }
        float effectScale = minecraft.options.fovEffectScale().get().floatValue();
        float scaled = baseFov * (1.0F + (fov - 1.0F) * effectScale);
        return Math.min(scaled, Math.max(baseFov, MAX_FOV));
    }

    public static void render(Minecraft minecraft, RenderTarget mainTarget) {
        if (phase == Phase.IDLE || pipelineFailed) {
            return;
        }
        GpuTexture color = mainTarget.getColorTexture();
        GpuTextureView colorView = mainTarget.getColorTextureView();
        if (color == null || colorView == null) {
            return;
        }
        if (pipeline == null) {
            pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(MultisessionClient.MOD_ID, "pipeline/warp"))
                    .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(MultisessionClient.MOD_ID, "post/warp"))
                    .withBindGroupLayout(BindGroupLayout.builder()
                            .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("WarpConfig", UniformType.UNIFORM_BUFFER)
                            .build())
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .build();
        }
        if (RenderSystem.getCompiledPipelineNullable(pipeline) == null) {
            pipelineFailed = true;
            LOGGER.error("Session switch effect is off: its shader did not compile");
            return;
        }

        int width = color.getWidth(0);
        int height = color.getHeight(0);
        if (sceneCopy == null) {
            sceneCopy = new TextureTarget("Multisession warp", width, height, color.getFormat(), null);
        } else if (sceneCopy.width != width || sceneCopy.height != height) {
            sceneCopy.resize(width, height);
        }
        if (uniforms == null) {
            uniforms = new MappableRingBuffer(() -> "Multisession warp", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UNIFORM_SIZE);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(color, sceneCopy.getColorTexture(), 0, 0, 0, 0, 0, width, height);
        try (GpuBufferSlice.MappedView view = uniforms.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec2(width, height)
                    .putFloat(travel)
                    .putFloat(warp)
                    .putFloat(flash)
                    .putFloat(minecraft.options.screenEffectScale().get().floatValue())
                    .putFloat(phase == Phase.ARRIVAL ? 1.0F : 0.0F);
        }
        try (RenderPass pass = encoder.createRenderPass(() -> "Multisession warp", colorView, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("WarpConfig", uniforms.currentBuffer());
            pass.setUniform("InSampler", sceneCopy.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
        uniforms.rotate();
    }

    private static void playSound(Minecraft minecraft, SoundEvent sound) {
        if (MultisessionConfig.get(MultisessionConfig.Option.WARP_SOUND)) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 0.8F));
        }
    }

    private static void launch(float progress) {
        if (progress < 0.2F) {
            float windUp = progress / 0.2F;
            warp = 0.08F * windUp;
            fov = 1.0F - 0.06F * Mth.sin(windUp * Mth.HALF_PI);
        } else {
            float rush = (progress - 0.2F) / 0.8F;
            warp = 0.08F + 0.92F * rush * rush;
            fov = 0.94F + (PEAK_FOV - 0.94F) * rush * rush * rush;
        }
        float glare = Math.max(0.0F, (progress - 0.62F) / 0.38F);
        flash = glare * glare;
    }

    private static void arrival(float progress) {
        warp = (float) Math.pow(1.0F - progress, 2.2);
        float fade = Math.min(progress / 0.4F, 1.0F);
        flash = 1.0F - fade * fade * (3.0F - 2.0F * fade);
        float back = progress - 1.0F;
        float settle = 1.0F + 2.2F * back * back * back + 1.2F * back * back;
        fov = 1.0F + (PEAK_FOV - 1.0F) * (1.0F - settle);
    }

    private enum Phase {
        IDLE,
        LAUNCH,
        ARRIVAL
    }
}
