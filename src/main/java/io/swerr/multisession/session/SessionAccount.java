package io.swerr.multisession.session;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import io.swerr.multisession.mixin.MinecraftAccessor;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.server.Services;
import org.jspecify.annotations.Nullable;

record SessionAccount(
        User user,
        Services services,
        CompletableFuture<@Nullable ProfileResult> profile,
        UserApiService userApiService,
        CompletableFuture<UserApiService.UserProperties> userProperties,
        PlayerSocialManager socialManager,
        RemoteFriendListUpdateHandler friendList,
        ClientTelemetryManager telemetry,
        ProfileKeyPairManager profileKeys) {

    static SessionAccount capture(Minecraft minecraft) {
        MinecraftAccessor fields = (MinecraftAccessor) minecraft;
        return new SessionAccount(
                fields.multisession$user(),
                fields.multisession$services(),
                fields.multisession$profileFuture(),
                fields.multisession$userApiService(),
                fields.multisession$userPropertiesFuture(),
                fields.multisession$playerSocialManager(),
                fields.multisession$friendList(),
                fields.multisession$telemetryManager(),
                fields.multisession$profileKeyPairManager());
    }

    void install(Minecraft minecraft) {
        MinecraftAccessor fields = (MinecraftAccessor) minecraft;
        fields.multisession$setUser(user);
        fields.multisession$setServices(services);
        fields.multisession$setProfileFuture(profile);
        fields.multisession$setUserApiService(userApiService);
        fields.multisession$setUserPropertiesFuture(userProperties);
        fields.multisession$setPlayerSocialManager(socialManager);
        fields.multisession$setFriendList(friendList);
        fields.multisession$setTelemetryManager(telemetry);
        fields.multisession$setProfileKeyPairManager(profileKeys);
    }

    void handOverFriendList(SessionAccount next) {
        if (friendList == next.friendList) {
            return;
        }
        friendList.stop();
        if (next.socialManager.isFriendListEnabled()) {
            next.friendList.start();
        }
    }
}
