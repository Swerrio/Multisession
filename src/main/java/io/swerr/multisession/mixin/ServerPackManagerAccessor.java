package io.swerr.multisession.mixin;

import net.minecraft.client.resources.server.PackDownloader;
import net.minecraft.client.resources.server.PackReloadConfig;
import net.minecraft.client.resources.server.ServerPackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPackManager.class)
public interface ServerPackManagerAccessor {

    @Accessor("downloader")
    PackDownloader multisession$downloader();

    @Accessor("reloadConfig")
    PackReloadConfig multisession$reloadConfig();

    @Accessor("updateRequest")
    Runnable multisession$updateRequest();
}
