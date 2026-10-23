package io.swerr.multisession.client;

import io.swerr.multisession.session.ClientSession;
import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
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
        return !(minecraft.screen instanceof ConnectScreen);
    }

    public static void join(Minecraft minecraft, String name, String address) {
        if (isActive(minecraft, address)) {
            minecraft.setScreen(null);
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
                boolean local = minecraft.isLocalServer();
                minecraft.level.disconnect();
                if (local) {
                    minecraft.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
                } else {
                    minecraft.clearLevel();
                }
            }
        }
        ServerData data = serverData(minecraft, name, address);
        ConnectScreen.startConnecting(parent, minecraft, ServerAddress.parseString(data.ip), data);
    }

    public static @Nullable String activeAddress(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || connection.getConnection().isMemoryConnection()) {
            return null;
        }
        ServerData data = minecraft.getCurrentServer();
        return data != null ? data.ip : null;
    }

    private static boolean isActive(Minecraft minecraft, String address) {
        String active = activeAddress(minecraft);
        return active != null && MultisessionConfig.sameAddress(active, address)
                && minecraft.getConnection().getLocalGameProfile().getName().equals(minecraft.getUser().getName());
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
        return new ServerData(name, address.trim(), false);
    }
}
