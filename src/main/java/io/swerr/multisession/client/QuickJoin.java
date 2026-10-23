package io.swerr.multisession.client;

import io.swerr.multisession.session.ClientSession;
import io.swerr.multisession.session.LostSession;
import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jspecify.annotations.Nullable;

public final class QuickJoin {

    private QuickJoin() {
    }

    public static boolean isOpen(Minecraft minecraft, String address) {
        return isActive(minecraft, address) || parkedSession(minecraft, address) != null;
    }

    public static boolean canJoin(Minecraft minecraft, String address) {
        if (isActive(minecraft, address)) {
            return true;
        }
        if (parkedSession(minecraft, address) != null || minecraft.getConnection() != null && MultisessionConfig.get(MultisessionConfig.Option.NEW_SESSION)) {
            return SessionManager.canSwitch(minecraft) && (minecraft.getConnection() == null || SessionManager.canPark(minecraft));
        }
        return !(minecraft.gui.screen() instanceof ConnectScreen);
    }

    public static void join(Minecraft minecraft, String name, String address) {
        if (isActive(minecraft, address)) {
            minecraft.gui.setScreen(null);
            return;
        }
        ClientSession parked = parkedSession(minecraft, address);
        if (parked != null) {
            SessionWarp.switchTo(minecraft, parked);
            return;
        }

        Screen parent = new JoinMultiplayerScreen(new TitleScreen());
        if (minecraft.getConnection() != null) {
            if (MultisessionConfig.get(MultisessionConfig.Option.NEW_SESSION)) {
                if (!SessionManager.startNewSession(minecraft, parent)) {
                    return;
                }
            } else {
                minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
            }
        }
        ServerData data = serverData(minecraft, name, address);
        ConnectScreen.startConnecting(parent, minecraft, ServerAddress.parseString(data.ip), data, false, null);
    }

    public static boolean canReconnect(Minecraft minecraft) {
        return SessionManager.canSwitch(minecraft) && (minecraft.getConnection() == null || SessionManager.canPark(minecraft));
    }

    public static void reconnect(Minecraft minecraft, LostSession lost) {
        String address = lost.address();
        if (address == null || !canReconnect(minecraft)) {
            return;
        }
        Screen parent = new JoinMultiplayerScreen(new TitleScreen());
        if (minecraft.getConnection() != null && !SessionManager.startNewSession(minecraft, parent)) {
            return;
        }
        SessionManager.restoreAccount(minecraft, lost);
        ServerData data = serverData(minecraft, lost.name(), address);
        ConnectScreen.startConnecting(parent, minecraft, ServerAddress.parseString(data.ip), data, false, null);
    }

    public static @Nullable String activeAddress(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || connection.getConnection().isMemoryConnection()) {
            return null;
        }
        ServerData data = connection.getServerData();
        return data != null ? data.ip : null;
    }

    private static boolean isActive(Minecraft minecraft, String address) {
        String active = activeAddress(minecraft);
        return active != null && MultisessionConfig.sameAddress(active, address)
                && minecraft.getConnection().getLocalGameProfile().name().equals(minecraft.getUser().getName());
    }

    private static @Nullable ClientSession parkedSession(Minecraft minecraft, String address) {
        String nickname = minecraft.getUser().getName();
        for (ClientSession session : SessionManager.parked()) {
            if (session.address() != null && MultisessionConfig.sameAddress(session.address(), address) && session.nickname().equals(nickname)) {
                return session;
            }
        }
        return null;
    }

    private static ServerData serverData(Minecraft minecraft, String name, String address) {
        ServerList servers = new ServerList(minecraft);
        servers.load();
        for (int i = 0; i < servers.size(); i++) {
            ServerData data = servers.get(i);
            if (MultisessionConfig.sameAddress(data.ip, address)) {
                return data;
            }
        }
        return new ServerData(name, address.trim(), ServerData.Type.OTHER);
    }
}
