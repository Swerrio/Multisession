package io.swerr.multisession.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import io.swerr.multisession.session.ClientSession;
import io.swerr.multisession.session.SessionManager;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.ShaderProgram;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
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
            ResourceLocation.fromNamespaceAndPath(MultisessionClient.MOD_ID, "warp.launch"));
    private static final SoundEvent ARRIVAL_SOUND = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MultisessionClient.MOD_ID, "warp.arrival"));
    private static final ShaderProgram PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MultisessionClient.MOD_ID, "core/warp"), DefaultVertexFormat.BLIT_SCREEN, ShaderDefines.EMPTY);

    private static LongSupplier clock = System::nanoTime;
    private static Phase phase = Phase.IDLE;
    private static @Nullable ClientSession target;
    private static long phaseStart;
    private static long lastFrame;
    private static float travel;
    private static float warp;
    private static float flash;
    private static float fov = 1.0F;
    private static @Nullable TextureTarget sceneCopy;
    private static boolean pipelineFailed;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_COLOR_BUFFER_BIT = 0x4000;
    private static final int GL_NEAREST = 0x2600;

    private SessionWarp() {
    }

    public static boolean active() {
        return phase != Phase.IDLE;
    }

    public static boolean holdsPause() {
        return phase == Phase.LAUNCH;
    }

    public static void cycle(Minecraft minecraft) {
        ClientSession next = SessionManager.next();
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
        minecraft.setScreen(null);
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
        CompiledShaderProgram shader = RenderSystem.setShader(PROGRAM);
        if (shader == null) {
            pipelineFailed = true;
            LOGGER.error("Session switch effect is off: its shader did not compile");
            return;
        }

        int width = mainTarget.width;
        int height = mainTarget.height;
        if (sceneCopy == null) {
            sceneCopy = new TextureTarget(width, height, false);
        } else if (sceneCopy.width != width || sceneCopy.height != height) {
            sceneCopy.resize(width, height);
        }

        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, mainTarget.frameBufferId);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, sceneCopy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        mainTarget.bindWrite(true);

        shader.bindSampler("InSampler", sceneCopy.getColorTextureId());
        shader.safeGetUniform("OutSize").set((float) width, (float) height);
        shader.safeGetUniform("Travel").set(travel);
        shader.safeGetUniform("Warp").set(warp);
        shader.safeGetUniform("Flash").set(flash);
        shader.safeGetUniform("Distortion").set(minecraft.options.screenEffectScale().get().floatValue());
        shader.safeGetUniform("Arrival").set(phase == Phase.ARRIVAL ? 1.0F : 0.0F);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
        quad.addVertex(0.0F, 0.0F, 0.0F);
        quad.addVertex(1.0F, 0.0F, 0.0F);
        quad.addVertex(1.0F, 1.0F, 0.0F);
        quad.addVertex(0.0F, 1.0F, 0.0F);
        BufferUploader.drawWithShader(quad.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
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
