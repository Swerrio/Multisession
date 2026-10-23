package io.swerr.multisession.client;

import io.swerr.multisession.session.ClientSession;
import io.swerr.multisession.session.LostSession;
import io.swerr.multisession.session.SessionManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

public final class SessionCards {

    private static final long HURT_NANOS = TimeUnit.SECONDS.toNanos(5);

    private static final List<Connection> ORDER = new ArrayList<>();
    private static final Map<Connection, Activity> ACTIVITY = new HashMap<>();
    private static final List<ClosingWorld> CLOSING = new ArrayList<>();

    private SessionCards() {
    }

    public static List<Connection> order(Minecraft minecraft) {
        sync(minecraft);
        return List.copyOf(ORDER);
    }

    public static void move(Connection key, int index) {
        if (ORDER.remove(key)) {
            ORDER.add(Mth.clamp(index, 0, ORDER.size()), key);
        }
    }

    public static @Nullable ClientSession parked(Connection key) {
        for (ClientSession session : SessionManager.parked()) {
            if (session.connection() == key) {
                return session;
            }
        }
        return null;
    }

    public static @Nullable ClientSession next(Minecraft minecraft) {
        List<Connection> order = order(minecraft);
        ClientPacketListener listener = minecraft.getConnection();
        int start = listener != null ? order.indexOf(listener.getConnection()) : -1;
        for (int step = 1; step <= order.size(); step++) {
            ClientSession session = parked(order.get(Math.floorMod(start + step, order.size())));
            if (session != null) {
                return session;
            }
        }
        return null;
    }

    public static void onParked(Connection key) {
        Activity activity = activity(key);
        activity.parkedAt = System.nanoTime();
        activity.unread = 0;
        activity.hurtAt = 0L;
        activity.lastHealth = Float.NaN;
    }

    public static void onActivated(Connection key) {
        Activity activity = activity(key);
        activity.unread = 0;
        activity.hurtAt = 0L;
    }

    public static void onChatMessage() {
        ClientSession session = SessionManager.background();
        if (session != null) {
            activity(session.connection()).unread++;
        }
    }

    public static int unread(Connection key) {
        Activity activity = ACTIVITY.get(key);
        return activity != null ? activity.unread : 0;
    }

    public static boolean hurt(Connection key) {
        Activity activity = ACTIVITY.get(key);
        return activity != null && activity.hurtAt != 0L && System.nanoTime() - activity.hurtAt < HURT_NANOS;
    }

    public static long awayNanos(Connection key) {
        Activity activity = ACTIVITY.get(key);
        return activity != null && activity.parkedAt != 0L ? System.nanoTime() - activity.parkedAt : 0L;
    }

    public static void closing(ClientSession session) {
        IntegratedServer server = session.world();
        if (server != null) {
            CLOSING.add(new ClosingWorld(session.connection(), session.label(), server));
        }
    }

    public static List<ClosingWorld> closing() {
        return List.copyOf(CLOSING);
    }

    public static Component activeName(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return Component.empty();
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            return Component.literal(server.getWorldData().getLevelName());
        }
        ServerData data = connection.getServerData();
        if (data != null) {
            return MultisessionConfig.displayName(data.name.isBlank() ? data.ip : data.name, data.ip);
        }
        String remote = String.valueOf(connection.getConnection().getRemoteAddress());
        return MultisessionConfig.displayName(remote, remote);
    }

    public static void tick(Minecraft minecraft) {
        long now = System.nanoTime();
        for (ClientSession session : SessionManager.parked()) {
            LocalPlayer player = session.player();
            if (player == null) {
                continue;
            }
            Activity activity = activity(session.connection());
            float health = player.getHealth();
            if (health < activity.lastHealth) {
                activity.hurtAt = now;
            }
            activity.lastHealth = health;
        }
        CLOSING.removeIf(world -> world.server().isShutdown());
        dismissRejoined(minecraft);
        sync(minecraft);
    }

    private static void dismissRejoined(Minecraft minecraft) {
        String address = QuickJoin.activeAddress(minecraft);
        if (address == null || minecraft.player == null) {
            return;
        }
        String nickname = minecraft.getConnection().getLocalGameProfile().name();
        for (LostSession lost : SessionManager.lost()) {
            if (lost.address() != null && MultisessionConfig.sameAddress(lost.address(), address) && lost.nickname().equals(nickname)) {
                SessionManager.dismiss(minecraft, lost);
            }
        }
    }

    private static void sync(Minecraft minecraft) {
        Set<Connection> live = new LinkedHashSet<>();
        ClientPacketListener listener = minecraft.getConnection();
        if (listener != null) {
            live.add(listener.getConnection());
        }
        for (ClientSession session : SessionManager.parked()) {
            live.add(session.connection());
        }
        ORDER.retainAll(live);
        for (Connection key : live) {
            if (!ORDER.contains(key)) {
                ORDER.add(key);
            }
        }

        Set<Connection> shown = new HashSet<>(live);
        for (LostSession lost : SessionManager.lost()) {
            shown.add(lost.connection());
        }
        for (ClosingWorld world : CLOSING) {
            shown.add(world.key());
        }
        ACTIVITY.keySet().retainAll(shown);
        SessionSnapshots.retainOnly(shown);
    }

    private static Activity activity(Connection key) {
        return ACTIVITY.computeIfAbsent(key, ignored -> new Activity());
    }

    public record ClosingWorld(Connection key, Component label, IntegratedServer server) {
    }

    private static final class Activity {

        private int unread;
        private long hurtAt;
        private long parkedAt;
        private float lastHealth = Float.NaN;
    }
}
