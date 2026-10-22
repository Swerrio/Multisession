package io.swerr.multisession.session;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import io.swerr.multisession.client.MultisessionConfig;
import io.swerr.multisession.mixin.ClientCommonPacketListenerImplAccessor;
import io.swerr.multisession.mixin.HudAccessor;
import io.swerr.multisession.mixin.MinecraftAccessor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class SessionManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component CLOSED = Component.translatable("multisession.disconnect.closed");
    private static final Component LOST = Component.translatable("multisession.disconnect.lost");

    private static final Map<Connection, ClientSession> PARKED = new LinkedHashMap<>();
    private static final Set<IntegratedServer> PAUSED_SERVERS = ConcurrentHashMap.newKeySet();
    private static final List<IntegratedServer> STOPPING_SERVERS = new ArrayList<>();
    private static volatile @Nullable ClientSession background;
    private static boolean activeIsLocal;

    private SessionManager() {
    }

    public static boolean inBackground() {
        return background != null;
    }

    public static @Nullable ClientSession background() {
        return background;
    }

    public static boolean protectsGlobalRegistries() {
        return background != null && activeIsLocal;
    }

    public static boolean isParkedServer(IntegratedServer server) {
        return PAUSED_SERVERS.contains(server);
    }

    public static boolean hasParked() {
        return !PARKED.isEmpty();
    }

    public static List<ClientSession> parked() {
        return List.copyOf(PARKED.values());
    }

    public static List<PackState> parkedPacks() {
        return PARKED.values().stream().map(session -> session.context.packs).toList();
    }

    public static @Nullable ClientSession parkedFor(Connection connection) {
        return PARKED.get(connection);
    }

    public static @Nullable ClientSession parkedFor(PacketListener listener) {
        if (PARKED.isEmpty() || !(listener instanceof ClientCommonPacketListenerImpl common)) {
            return null;
        }
        return PARKED.get(((ClientCommonPacketListenerImplAccessor) common).multisession$connection());
    }

    public static boolean canPark(Minecraft minecraft) {
        if (minecraft.getConnection() == null || minecraft.level == null || minecraft.player == null || minecraft.gameMode == null) {
            return false;
        }
        if (!minecraft.isLocalServer()) {
            return true;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        return server != null && !server.isPublished();
    }

    public static boolean canSwitch(Minecraft minecraft) {
        if (inBackground() || minecraft.gui.screen() instanceof ConnectScreen) {
            return false;
        }
        return minecraft.getConnection() == null ? minecraft.level == null : canPark(minecraft);
    }

    public static void startNewSession(Minecraft minecraft) {
        startNewSession(minecraft, new JoinMultiplayerScreen(new TitleScreen()));
    }

    public static boolean startNewSession(Minecraft minecraft, Screen next) {
        if (!canSwitch(minecraft) || !canPark(minecraft)) {
            return false;
        }

        ClientSession parked = park(minecraft);
        SessionContext fresh = SessionContext.fresh(minecraft);
        fresh.install(minecraft);
        parked.context.packs.completePendingReload();
        ((MinecraftAccessor) minecraft).multisession$updateLevelInEngines(null);
        if (!((PackSourceAccess) minecraft.getDownloadedPackSource()).multisession$keepLoadedPacks(fresh.packs)) {
            minecraft.reloadResourcePacks();
        }
        minecraft.gui.setScreen(next);
        return true;
    }

    public static @Nullable ClientSession next() {
        return PARKED.isEmpty() ? null : PARKED.values().iterator().next();
    }

    public static void activate(Minecraft minecraft, ClientSession target) {
        if (!PARKED.containsKey(target.connection) || !canSwitch(minecraft)) {
            return;
        }

        boolean outgoingParked = minecraft.getConnection() != null;
        SessionContext outgoing = outgoingParked ? park(minecraft).context : SessionContext.capture(minecraft);
        PARKED.remove(target.connection);

        SessionContext context = target.context;
        ((HudAccessor) context.hud).multisession$setHidden(outgoing.hud.isHidden());
        context.chatListener.setMessageDelay(minecraft.options.chatDelay().get());
        context.install(minecraft);
        outgoing.packs.completePendingReload();
        outgoing.account.handOverFriendList(context.account);
        RemoteFriendListUpdateHandler outgoingFriendList = outgoing.account.friendList();
        if (!outgoingParked && outgoingFriendList != context.account.friendList() && !usesFriendList(outgoingFriendList)) {
            outgoingFriendList.close();
        }
        minecraft.hitResult = null;
        minecraft.crosshairPickEntity = null;
        if (context.player != null) {
            context.player.input = target.savedInput != null ? target.savedInput : new KeyboardInput(minecraft.options);
            RegistryBinding.bindTags(context.player.connection);
            RegistryBinding.bindComponents(context.player.connection);
        }
        if (context.singleplayerServer != null) {
            PAUSED_SERVERS.remove(context.singleplayerServer);
        }

        ((MinecraftAccessor) minecraft).multisession$updateLevelInEngines(context.level);
        if (context.level != null) {
            WorldEvents.loadedChunks(context.level).multisession$replayLoadedChunks();
            Entity camera = target.camera();
            minecraft.setCameraEntity(camera != null && !camera.isRemoved() ? camera : context.player);
            WorldEvents.announceLoaded(context.level);
        }

        if (!((PackSourceAccess) minecraft.getDownloadedPackSource()).multisession$keepLoadedPacks(context.packs)) {
            minecraft.reloadResourcePacks();
        }

        Screen screen = target.pendingScreen();
        target.setPendingScreen(null);
        minecraft.gui.setScreen(screen instanceof LevelLoadingScreen ? null : screen);
    }

    public static void close(Minecraft minecraft, ClientSession session) {
        if (!PARKED.containsKey(session.connection) || inBackground()) {
            return;
        }

        session.closedByUser = true;
        runInBackground(minecraft, session, () -> {
            session.connection.disconnect(CLOSED);
            session.connection.handleDisconnection();
        });
        if (session.disconnectReason == null) {
            session.disconnectReason = CLOSED;
        }
        reapDisconnected(minecraft);
    }

    public static void tickBackground(Minecraft minecraft) {
        STOPPING_SERVERS.removeIf(server -> {
            if (!server.isShutdown()) {
                return false;
            }
            PAUSED_SERVERS.remove(server);
            return true;
        });
        if (PARKED.isEmpty() || inBackground()) {
            return;
        }

        for (ClientSession session : List.copyOf(PARKED.values())) {
            runInBackground(minecraft, session, () -> session.tick(minecraft));
        }
        reapDisconnected(minecraft);
    }

    public static void onParkedDisconnect(Connection connection, @Nullable Component reason) {
        ClientSession session = PARKED.get(connection);
        if (session != null && session.disconnectReason == null) {
            session.disconnectReason = reason != null ? reason : LOST;
        }
    }

    public static void onBackgroundDisconnect() {
        ClientSession session = background;
        if (session != null && session.disconnectReason == null) {
            session.disconnectReason = LOST;
            session.connection.disconnect(LOST);
        }
    }

    public static void onBackgroundReconfigure(Minecraft minecraft, Screen screen) {
        ClientSession session = background;
        if (session == null) {
            return;
        }

        ClientPacketListener listener = minecraft.getConnection();
        if (listener != null) {
            listener.clearLevel();
        }
        session.setPendingScreen(screen);
        session.setCamera(null);
        minecraft.level = null;
        minecraft.player = null;
        minecraft.gameMode = null;
    }

    public static boolean hasWorld(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() != null) {
            return true;
        }
        for (ClientSession session : PARKED.values()) {
            if (session.context.singleplayerServer != null) {
                return true;
            }
        }
        return false;
    }

    public static void closeParkedWorlds(Minecraft minecraft) {
        for (ClientSession session : List.copyOf(PARKED.values())) {
            IntegratedServer server = session.context.singleplayerServer;
            if (server == null) {
                continue;
            }
            LOGGER.info("Saving and closing background world {} before another world starts", session.name());
            close(minecraft, session);
            minecraft.gui.setScreen(new GenericMessageScreen(Gui.SAVING_LEVEL));
            waitForShutdown(minecraft, server);
            SystemToast.add(
                    minecraft.gui.toastManager(),
                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.translatable("multisession.toast.world_closed", session.name()),
                    Component.translatable("multisession.toast.world_closed.reason"));
        }
    }

    public static void shutdown() {
        List<ClientSession> sessions = List.copyOf(PARKED.values());
        PARKED.clear();
        RemoteFriendListUpdateHandler activeFriendList = ((MinecraftAccessor) Minecraft.getInstance()).multisession$friendList();
        for (ClientSession session : sessions) {
            session.connection.disconnect(Component.translatable("disconnect.quitting"));
            stopWorld(session);
            if (session.context.account.friendList() != activeFriendList) {
                session.context.account.friendList().close();
            }
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        for (IntegratedServer server : STOPPING_SERVERS) {
            while (!server.isShutdown() && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
        }
        STOPPING_SERVERS.clear();
        PAUSED_SERVERS.clear();
    }

    public static @Nullable ClientSession parkedWorld(String levelId) {
        for (ClientSession session : PARKED.values()) {
            if (levelId.equals(session.worldId)) {
                return session;
            }
        }
        return null;
    }

    public static boolean usesFriendList(RemoteFriendListUpdateHandler friendList) {
        for (ClientSession session : PARKED.values()) {
            if (session.context.account.friendList() == friendList) {
                return true;
            }
        }
        return false;
    }

    private static ClientSession park(Minecraft minecraft) {
        ClientPacketListener listener = minecraft.getConnection();
        WorldEvents.announceUnloaded(minecraft.level);
        ClientSession session = new ClientSession(
                listener.getConnection(), displayName(listener), listener.getLocalGameProfile().name(), SessionContext.capture(minecraft));
        session.setCamera(minecraft.getCameraEntity());
        session.savedInput = minecraft.player.input;
        minecraft.player.input = new ClientInput();
        IntegratedServer server = session.context.singleplayerServer;
        if (server != null) {
            session.worldId = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        } else if (listener.getServerData() != null) {
            session.address = listener.getServerData().ip;
        }
        PARKED.put(session.connection, session);
        if (server != null) {
            pauseWorld(server);
        }
        minecraft.gameRenderer.resetData();
        minecraft.hitResult = null;
        minecraft.crosshairPickEntity = null;
        return session;
    }

    private static void pauseWorld(IntegratedServer server) {
        PAUSED_SERVERS.add(server);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250);
        while (!server.isPaused() && !server.isShutdown() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    private static void waitForShutdown(Minecraft minecraft, IntegratedServer server) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!server.isShutdown() && System.nanoTime() < deadline) {
            RenderSystem.pumpEvents(((MinecraftAccessor) minecraft).multisession$sdlEventHandler());
            minecraft.renderFrame(false);
        }
        STOPPING_SERVERS.remove(server);
        PAUSED_SERVERS.remove(server);
    }

    private static void stopWorld(ClientSession session) {
        IntegratedServer server = session.context.singleplayerServer;
        if (server != null && !server.isShutdown() && !STOPPING_SERVERS.contains(server)) {
            LOGGER.info("Saving and stopping background world {}", session.name());
            server.halt(false);
            STOPPING_SERVERS.add(server);
        }
    }

    private static String displayName(ClientPacketListener listener) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (listener.getConnection().isMemoryConnection() && server != null) {
            return server.getWorldData().getLevelName();
        }
        ServerData data = listener.getServerData();
        if (data != null && !data.name.isBlank()) {
            return data.name;
        }
        return data != null ? data.ip : String.valueOf(listener.getConnection().getRemoteAddress());
    }

    private static void runInBackground(Minecraft minecraft, ClientSession session, Runnable work) {
        HitResult hitResult = minecraft.hitResult;
        Entity crosshairPickEntity = minecraft.crosshairPickEntity;
        SessionContext active;
        synchronized (FabricAddons.LOCK) {
            FabricAddons.beginSwap();
            active = SessionContext.capture(minecraft);
            activeIsLocal = active.localServer;
            background = session;
            session.context.install(minecraft);
        }
        minecraft.hitResult = null;
        minecraft.crosshairPickEntity = null;
        try {
            if (!activeIsLocal && session.context.singleplayerServer == null && session.context.player != null) {
                RegistryBinding.bindTags(session.context.player.connection);
            }
            work.run();
        } catch (RuntimeException e) {
            LOGGER.error("Background session {} failed", session.name(), e);
            session.disconnectReason = Component.translatable("multisession.disconnect.error", e.toString());
            session.connection.disconnect(session.disconnectReason);
        } finally {
            synchronized (FabricAddons.LOCK) {
                session.context = SessionContext.capture(minecraft);
                active.install(minecraft);
                FabricAddons.endSwap();
            }
            minecraft.hitResult = hitResult;
            minecraft.crosshairPickEntity = crosshairPickEntity;
            background = null;
            ClientPacketListener listener = minecraft.getConnection();
            if (!activeIsLocal && listener != null) {
                RegistryBinding.bindTags(listener);
                RegistryBinding.bindComponents(listener);
            }
        }
    }

    private static void reapDisconnected(Minecraft minecraft) {
        Iterator<ClientSession> iterator = PARKED.values().iterator();
        while (iterator.hasNext()) {
            ClientSession session = iterator.next();
            if (session.disconnectReason == null) {
                continue;
            }

            iterator.remove();
            stopWorld(session);
            RemoteFriendListUpdateHandler friendList = session.context.account.friendList();
            if (friendList != ((MinecraftAccessor) minecraft).multisession$friendList() && !usesFriendList(friendList)) {
                friendList.close();
            }
            LOGGER.info("Background session {} ({}) disconnected: {}", session.name(), session.nickname(), session.disconnectReason.getString());
            if (!session.closedByUser && MultisessionConfig.get(MultisessionConfig.Option.DISCONNECT_TOASTS)) {
                SystemToast.add(
                        minecraft.gui.toastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable("multisession.toast.disconnected", session.label()),
                        session.disconnectReason);
            }
        }
    }
}
