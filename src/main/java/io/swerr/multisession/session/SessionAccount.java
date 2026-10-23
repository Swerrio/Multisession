package io.swerr.multisession.session;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.properties.PropertyMap;
import io.swerr.multisession.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import com.mojang.authlib.minecraft.MinecraftSessionService;

record SessionAccount(
        User user,
        MinecraftSessionService sessionService,
        PropertyMap profileProperties,
        UserApiService userApiService,
        PlayerSocialManager socialManager,
        ProfileKeyPairManager profileKeys) {

    static SessionAccount capture(Minecraft minecraft) {
        MinecraftAccessor fields = (MinecraftAccessor) minecraft;
        return new SessionAccount(
                fields.multisession$user(),
                fields.multisession$sessionService(),
                fields.multisession$profileProperties(),
                fields.multisession$userApiService(),
                fields.multisession$playerSocialManager(),
                fields.multisession$profileKeyPairManager());
    }

    void install(Minecraft minecraft) {
        MinecraftAccessor fields = (MinecraftAccessor) minecraft;
        fields.multisession$setUser(user);
        fields.multisession$setSessionService(sessionService);
        fields.multisession$setProfileProperties(profileProperties);
        fields.multisession$setUserApiService(userApiService);
        fields.multisession$setPlayerSocialManager(socialManager);
        fields.multisession$setProfileKeyPairManager(profileKeys);
    }
}
