package io.swerr.multisession.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.properties.PropertyMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.server.IntegratedServer;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Invoker("updateLevelInEngines")
    void multisession$updateLevelInEngines(@Nullable ClientLevel level);

    @Invoker("runTick")
    void multisession$runTick(boolean advanceGameTime);

    @Mutable
    @Accessor("gui")
    void multisession$setGui(Gui gui);

    @Mutable
    @Accessor("toast")
    void multisession$setToastManager(ToastComponent toasts);

    @Mutable
    @Accessor("chatListener")
    void multisession$setChatListener(ChatListener chatListener);

    @Accessor("reportingContext")
    void multisession$setReportingContext(ReportingContext reportingContext);

    @Accessor("singleplayerServer")
    @Nullable IntegratedServer multisession$singleplayerServer();

    @Accessor("currentServer")
    @Nullable ServerData multisession$currentServer();

    @Accessor("currentServer")
    void multisession$setCurrentServer(@Nullable ServerData currentServer);

    @Accessor("singleplayerServer")
    void multisession$setSingleplayerServer(@Nullable IntegratedServer singleplayerServer);

    @Accessor("isLocalServer")
    boolean multisession$isLocalServer();

    @Accessor("isLocalServer")
    void multisession$setLocalServer(boolean localServer);

    @Accessor("userApiService")
    UserApiService multisession$userApiService();

    @Mutable
    @Accessor("userApiService")
    void multisession$setUserApiService(UserApiService userApiService);

    @Accessor("user")
    User multisession$user();

    @Mutable
    @Accessor("user")
    void multisession$setUser(User user);

    @Accessor("minecraftSessionService")
    MinecraftSessionService multisession$sessionService();

    @Mutable
    @Accessor("minecraftSessionService")
    void multisession$setSessionService(MinecraftSessionService sessionService);

    @Accessor("profileProperties")
    PropertyMap multisession$profileProperties();

    @Mutable
    @Accessor("profileProperties")
    void multisession$setProfileProperties(PropertyMap profileProperties);

    @Accessor("playerSocialManager")
    PlayerSocialManager multisession$playerSocialManager();

    @Mutable
    @Accessor("playerSocialManager")
    void multisession$setPlayerSocialManager(PlayerSocialManager playerSocialManager);

    @Accessor("profileKeyPairManager")
    ProfileKeyPairManager multisession$profileKeyPairManager();

    @Mutable
    @Accessor("profileKeyPairManager")
    void multisession$setProfileKeyPairManager(ProfileKeyPairManager profileKeyPairManager);
}
