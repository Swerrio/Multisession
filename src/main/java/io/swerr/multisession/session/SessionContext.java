package io.swerr.multisession.session;

import io.swerr.multisession.mixin.GuiAccessor;
import io.swerr.multisession.mixin.HudAccessor;
import io.swerr.multisession.mixin.MinecraftAccessor;
import io.swerr.multisession.mixin.OptionsAccessor;
import net.fabricmc.fabric.impl.networking.client.ClientConfigurationNetworkAddon;
import net.fabricmc.fabric.impl.networking.client.ClientNetworkingImpl;
import net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
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
    Hud hud;
    ToastManager toasts;
    ChatListener chatListener;
    ReportingContext reporting;
    int serverRenderDistance;
    PackState packs;
    @Nullable ClientPlayNetworkAddon playAddon;
    @Nullable ClientConfigurationNetworkAddon configurationAddon;
    SessionAccount account;
    @Nullable IntegratedServer singleplayerServer;
    boolean localServer;

    private SessionContext() {
    }

    static SessionContext capture(Minecraft minecraft) {
        SessionContext context = new SessionContext();
        context.level = minecraft.level;
        context.player = minecraft.player;
        context.gameMode = minecraft.gameMode;
        context.hud = minecraft.gui.hud;
        context.toasts = minecraft.gui.toastManager();
        context.chatListener = minecraft.gui.chatListener();
        context.reporting = minecraft.getReportingContext();
        context.serverRenderDistance = ((OptionsAccessor) minecraft.options).multisession$serverRenderDistance();
        context.packs = ((PackSourceAccess) minecraft.getDownloadedPackSource()).multisession$installedPacks();
        context.playAddon = ClientNetworkingImpl.getClientPlayAddon();
        context.configurationAddon = ClientNetworkingImpl.getClientConfigurationAddon();
        context.account = SessionAccount.capture(minecraft);
        context.singleplayerServer = ((MinecraftAccessor) minecraft).multisession$singleplayerServer();
        context.localServer = ((MinecraftAccessor) minecraft).multisession$isLocalServer();
        return context;
    }

    static SessionContext fresh(Minecraft minecraft) {
        SessionContext context = new SessionContext();
        HudAccessor current = (HudAccessor) minecraft.gui.hud;
        Hud hud = new Hud(minecraft);
        HudAccessor created = (HudAccessor) hud;
        created.multisession$setWaypointStyles(current.multisession$waypointStyles());
        created.multisession$setDebugOverlay(current.multisession$debugOverlay());
        created.multisession$setSubtitleOverlay(current.multisession$subtitleOverlay());
        created.multisession$setHidden(minecraft.gui.hud.isHidden());
        context.hud = hud;
        context.toasts = new ToastManager(minecraft, minecraft.options);
        context.chatListener = new ChatListener(minecraft);
        context.chatListener.setMessageDelay(minecraft.options.chatDelay().get());
        context.reporting = ReportingContext.create(ReportEnvironment.local(), ((MinecraftAccessor) minecraft).multisession$userApiService());
        context.serverRenderDistance = 0;
        context.packs = ((PackSourceAccess) minecraft.getDownloadedPackSource()).multisession$newPacks();
        context.account = SessionAccount.capture(minecraft);
        return context;
    }

    void install(Minecraft minecraft) {
        minecraft.level = level;
        minecraft.player = player;
        minecraft.gameMode = gameMode;
        GuiAccessor gui = (GuiAccessor) minecraft.gui;
        gui.multisession$setHud(hud);
        gui.multisession$setToastManager(toasts);
        gui.multisession$setChatListener(chatListener);
        ((MinecraftAccessor) minecraft).multisession$setReportingContext(reporting);
        minecraft.options.setServerRenderDistance(serverRenderDistance);
        ((PackSourceAccess) minecraft.getDownloadedPackSource()).multisession$installPacks(packs);
        ClientNetworkingImpl.setClientConfigurationAddon(null);
        ClientNetworkingImpl.setClientPlayAddon(playAddon);
        ClientNetworkingImpl.setClientConfigurationAddon(configurationAddon);
        account.install(minecraft);
        ((MinecraftAccessor) minecraft).multisession$setSingleplayerServer(singleplayerServer);
        ((MinecraftAccessor) minecraft).multisession$setLocalServer(localServer);
    }
}
