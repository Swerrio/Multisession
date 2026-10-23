package io.swerr.multisession.session;

import io.swerr.multisession.mixin.GuiAccessor;
import io.swerr.multisession.mixin.MinecraftAccessor;
import io.swerr.multisession.mixin.OptionsAccessor;
import net.fabricmc.fabric.impl.networking.client.ClientNetworkingImpl;
import net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import org.jspecify.annotations.Nullable;

final class SessionContext {

    @Nullable ClientLevel level;
    @Nullable LocalPlayer player;
    @Nullable MultiPlayerGameMode gameMode;
    Gui gui;
    ToastComponent toasts;
    ChatListener chatListener;
    ReportingContext reporting;
    int serverRenderDistance;
    PackState packs;
    @Nullable ClientPlayNetworkAddon playAddon;
    SessionAccount account;
    @Nullable IntegratedServer singleplayerServer;
    @Nullable ServerData serverData;
    boolean localServer;

    private SessionContext() {
    }

    static SessionContext capture(Minecraft minecraft) {
        SessionContext context = new SessionContext();
        context.level = minecraft.level;
        context.player = minecraft.player;
        context.gameMode = minecraft.gameMode;
        context.gui = minecraft.gui;
        context.toasts = minecraft.getToasts();
        context.chatListener = minecraft.getChatListener();
        context.reporting = minecraft.getReportingContext();
        context.serverRenderDistance = ((OptionsAccessor) minecraft.options).multisession$serverRenderDistance();
        context.packs = ((PackSourceAccess) minecraft.getClientPackSource()).multisession$installedPacks();
        context.playAddon = ClientNetworkingImpl.getClientPlayAddon();
        context.account = SessionAccount.capture(minecraft);
        context.singleplayerServer = ((MinecraftAccessor) minecraft).multisession$singleplayerServer();
        context.serverData = ((MinecraftAccessor) minecraft).multisession$currentServer();
        context.localServer = ((MinecraftAccessor) minecraft).multisession$isLocalServer();
        return context;
    }

    static SessionContext fresh(Minecraft minecraft) {
        SessionContext context = new SessionContext();
        GuiAccessor current = (GuiAccessor) minecraft.gui;
        Gui gui = new Gui(minecraft, minecraft.getItemRenderer());
        GuiAccessor created = (GuiAccessor) gui;
        created.multisession$setDebugOverlay(current.multisession$debugOverlay());
        created.multisession$setSubtitleOverlay(current.multisession$subtitleOverlay());
        context.gui = gui;
        context.toasts = new ToastComponent(minecraft);
        context.chatListener = new ChatListener(minecraft);
        context.chatListener.setMessageDelay(minecraft.options.chatDelay().get());
        context.reporting = ReportingContext.create(ReportEnvironment.local(), ((MinecraftAccessor) minecraft).multisession$userApiService());
        context.serverRenderDistance = 0;
        context.packs = ((PackSourceAccess) minecraft.getClientPackSource()).multisession$newPacks();
        context.account = SessionAccount.capture(minecraft);
        return context;
    }

    void install(Minecraft minecraft) {
        install(minecraft, true);
    }

    void install(Minecraft minecraft, boolean server) {
        minecraft.level = level;
        minecraft.player = player;
        minecraft.gameMode = gameMode;
        MinecraftAccessor fields = (MinecraftAccessor) minecraft;
        fields.multisession$setGui(gui);
        fields.multisession$setToastManager(toasts);
        fields.multisession$setChatListener(chatListener);
        fields.multisession$setReportingContext(reporting);
        minecraft.options.setServerRenderDistance(serverRenderDistance);
        ((PackSourceAccess) minecraft.getClientPackSource()).multisession$installPacks(packs);
        ClientNetworkingImpl.setClientPlayAddon(playAddon);
        account.install(minecraft);
        if (server) {
            fields.multisession$setSingleplayerServer(singleplayerServer);
            fields.multisession$setCurrentServer(serverData);
            fields.multisession$setLocalServer(localServer);
        }
    }

    SessionContext withServerOf(SessionContext other) {
        singleplayerServer = other.singleplayerServer;
        serverData = other.serverData;
        localServer = other.localServer;
        return this;
    }
}
