package io.swerr.multisession.mixin;

import io.swerr.multisession.session.PackSourceAccess;
import io.swerr.multisession.session.PackState;
import java.util.function.Consumer;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.Pack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPackSource.class)
public abstract class DownloadedPackSourceMixin implements PackSourceAccess {

    @Shadow
    private @Nullable Pack serverPack;

    @Unique
    private @Nullable Pack multisession$loaded;

    @Inject(method = "loadPacks", at = @At("HEAD"))
    private void multisession$rememberLoaded(Consumer<Pack> consumer, Pack.PackConstructor constructor, CallbackInfo ci) {
        this.multisession$loaded = this.serverPack;
    }

    @Override
    public PackState multisession$installedPacks() {
        return new PackState(this.serverPack);
    }

    @Override
    public PackState multisession$newPacks() {
        return new PackState(null);
    }

    @Override
    public void multisession$installPacks(PackState state) {
        this.serverPack = state.serverPack();
    }

    @Override
    public boolean multisession$keepLoadedPacks(PackState state) {
        return state.serverPack() == this.multisession$loaded;
    }
}
