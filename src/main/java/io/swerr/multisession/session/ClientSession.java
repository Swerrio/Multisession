package io.swerr.multisession.session;

import com.mojang.logging.LogUtils;
import io.swerr.multisession.client.MultisessionConfig;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundResourcePackPacket;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class ClientSession {

    private static final Logger LOGGER = LogUtils.getLogger();

    final Connection connection;
    private final String name;
    private final String nickname;
    private final Queue<DeferredPacket> deferredPackets = new ArrayDeque<>();
    private final Set<String> reportedFailures = new HashSet<>();

    SessionContext context;
    @Nullable Component disconnectReason;
    boolean closedByUser;
    @Nullable Input savedInput;
    @Nullable String worldId;
    @Nullable String address;
    private @Nullable Entity camera;
    private @Nullable Screen pendingScreen;
    private @Nullable ClientboundResourcePackPacket packRequest;

    ClientSession(Connection connection, String name, String nickname, SessionContext context) {
        this.connection = connection;
        this.name = name;
        this.nickname = nickname;
        this.context = context;
    }

    public String name() {
        return name;
    }

    public String nickname() {
        return nickname;
    }

    public Component label() {
        return Component.translatable("multisession.session.label", MultisessionConfig.displayName(name, address), nickname);
    }

    public @Nullable String address() {
        return address;
    }

    public @Nullable IntegratedServer world() {
        return context.singleplayerServer;
    }

    public boolean isLocal() {
        return context.localServer;
    }

    public boolean isInWorld() {
        return context.level != null && context.player != null && context.gameMode != null;
    }

    public @Nullable Entity camera() {
        return camera;
    }

    public void setCamera(@Nullable Entity camera) {
        this.camera = camera;
    }

    public @Nullable Screen pendingScreen() {
        return pendingScreen;
    }

    public void setPendingScreen(@Nullable Screen screen) {
        this.pendingScreen = screen;
    }

    public void deferPackRequest(ClientboundResourcePackPacket packet) {
        this.packRequest = packet;
    }

    @Nullable ClientboundResourcePackPacket takePackRequest() {
        ClientboundResourcePackPacket packet = this.packRequest;
        this.packRequest = null;
        return packet;
    }

    public void defer(DeferredPacket task) {
        deferredPackets.add(task);
    }

    void tick(Minecraft minecraft) {
        DeferredPacket task;
        while ((task = deferredPackets.poll()) != null) {
            task.handle();
        }

        if (disconnectReason != null) {
            return;
        }

        if (context.singleplayerServer != null) {
            if (!connection.isConnected()) {
                connection.handleDisconnection();
            }
            return;
        }


        LocalPlayer player = minecraft.player;
        if (player != null && player.input.getClass() != Input.class) {
            savedInput = player.input;
            player.input = new Input();
        }

        ClientLevel level = minecraft.level;
        MultiPlayerGameMode gameMode = minecraft.gameMode;
        if (level == null || player == null || gameMode == null) {
            phase("connection", () -> {
                if (connection.isConnected()) {
                    connection.tick();
                } else {
                    connection.handleDisconnection();
                }
            });
        } else {
            phase("connection", gameMode::tick);
            if (disconnectReason != null) {
                return;
            }

            phase("entities", level::tickEntities);
            phase("level", () -> level.tick(() -> true));
            phase("light", level::pollLightUpdates);
        }

        phase("chat", () -> minecraft.getChatListener().tick());
        phase("hud", () -> minecraft.gui.tick(false));
    }

    private void phase(String phase, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            if (reportedFailures.add(phase + ":" + e.getClass().getName())) {
                LOGGER.error("Background session {} failed while ticking {}; the session stays connected", name, phase, e);
            }
        }
    }
}
