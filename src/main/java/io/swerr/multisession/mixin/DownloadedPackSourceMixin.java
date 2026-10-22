package io.swerr.multisession.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.swerr.multisession.session.PackSourceAccess;
import io.swerr.multisession.session.PackState;
import io.swerr.multisession.session.SessionManager;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackDownloader;
import net.minecraft.client.resources.server.PackLoadFeedback;
import net.minecraft.client.resources.server.PackReloadConfig;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DownloadedPackSource.class)
public abstract class DownloadedPackSourceMixin implements PackSourceAccess {

    @Shadow
    @Final
    private static RepositorySource EMPTY_SOURCE;

    @Shadow
    @Final
    private static PackLoadFeedback LOG_ONLY_FEEDBACK;

    @Shadow
    @Final
    @Mutable
    private ServerPackManager manager;

    @Shadow
    private RepositorySource packSource;

    @Shadow
    private PackSource packType;

    @Shadow
    private PackLoadFeedback packFeedback;

    @Shadow
    private PackReloadConfig.@Nullable Callbacks pendingReload;

    @Unique
    private PackDownloader multisession$downloader;

    @Unique
    private PackReloadConfig multisession$reloadConfig;

    @Unique
    private Runnable multisession$updateRequest;

    @Unique
    private PackState multisession$installed;

    @Unique
    private List<PackReloadConfig.IdAndPath> multisession$loaded;

    @Unique
    private RepositorySource multisession$loadedSource;

    @Unique
    private List<PackReloadConfig.IdAndPath> multisession$preparing;

    @Shadow
    private @Nullable List<Pack> loadRequestedPacks(List<PackReloadConfig.IdAndPath> packsToLoad) {
        throw new AssertionError();
    }

    @Shadow
    private static RepositorySource configureSource(List<Pack> packs) {
        throw new AssertionError();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void multisession$giveEachSessionItsOwnManager(CallbackInfo ci) {
        ServerPackManagerAccessor vanilla = (ServerPackManagerAccessor) this.manager;
        this.multisession$downloader = vanilla.multisession$downloader();
        this.multisession$reloadConfig = vanilla.multisession$reloadConfig();
        this.multisession$updateRequest = vanilla.multisession$updateRequest();

        PackState state = new PackState();
        state.manager = this.multisession$createManager(state);
        state.packSource = this.packSource;
        state.packType = this.packType;
        state.feedback = this.packFeedback;
        state.installed = true;
        this.manager = state.manager;
        this.multisession$installed = state;
        this.multisession$loaded = List.of();
        this.multisession$loadedSource = this.packSource;
        this.multisession$preparing = List.of();
    }

    @Inject(method = "startReload", at = @At("HEAD"), cancellable = true)
    private void multisession$reloadOnlyForActiveSession(PackReloadConfig.Callbacks callbacks, CallbackInfo ci) {
        if (!SessionManager.inBackground()) {
            return;
        }

        List<Pack> packs = this.loadRequestedPacks(callbacks.packsToLoad());
        if (packs == null) {
            callbacks.onFailure(false);
            packs = this.loadRequestedPacks(callbacks.packsToLoad());
            if (packs == null) {
                packs = List.of();
            }
        }
        this.packSource = configureSource(packs);
        this.multisession$installed.requested = callbacks.packsToLoad();
        callbacks.onSuccess();
        ci.cancel();
    }

    @WrapOperation(method = "startReload", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/server/DownloadedPackSource;loadRequestedPacks(Ljava/util/List;)Ljava/util/List;"))
    private @Nullable List<Pack> multisession$loadWithOtherVersions(
            DownloadedPackSource source, List<PackReloadConfig.IdAndPath> packs, Operation<List<Pack>> original) {
        List<PackReloadConfig.IdAndPath> pooled = PackState.withOtherVersions(packs, SessionManager.parkedPacks());
        if (!pooled.equals(packs)) {
            List<Pack> loaded = original.call(source, pooled);
            if (loaded != null) {
                this.multisession$preparing = pooled;
                return loaded;
            }
        }
        this.multisession$preparing = packs;
        return original.call(source, packs);
    }

    @Inject(method = "startReload", at = @At("TAIL"))
    private void multisession$rememberActivePacks(PackReloadConfig.Callbacks callbacks, CallbackInfo ci) {
        this.multisession$installed.requested = callbacks.packsToLoad();
        this.multisession$rememberLoaded(this.multisession$preparing);
    }

    @Inject(method = "onRecovery", at = @At("TAIL"))
    private void multisession$rememberRecoveredPacks(CallbackInfo ci) {
        if (this.pendingReload != null) {
            this.multisession$rememberLoaded(this.pendingReload.packsToLoad());
        }
    }

    @Inject(method = "onRecoveryFailure", at = @At("TAIL"))
    private void multisession$forgetFailedPacks(CallbackInfo ci) {
        this.multisession$installed.requested = List.of();
        this.multisession$rememberLoaded(List.of());
    }

    @Override
    public boolean multisession$keepLoadedPacks(PackState state) {
        if (PackState.covers(this.multisession$loaded, state.requested)) {
            this.packSource = this.multisession$loadedSource;
            return true;
        }

        List<PackReloadConfig.IdAndPath> pooled = PackState.withOtherVersions(state.requested, SessionManager.parkedPacks());
        List<Pack> packs = this.loadRequestedPacks(pooled);
        if (packs == null) {
            pooled = state.requested;
            packs = this.loadRequestedPacks(pooled);
        }
        this.packSource = configureSource(packs != null ? packs : List.of());
        this.multisession$rememberLoaded(pooled);
        return false;
    }

    @Unique
    private void multisession$rememberLoaded(List<PackReloadConfig.IdAndPath> packs) {
        this.multisession$loaded = this.packSource == EMPTY_SOURCE ? List.of() : List.copyOf(packs);
        this.multisession$loadedSource = this.packSource;
    }

    @Override
    public PackState multisession$installedPacks() {
        PackState state = this.multisession$installed;
        this.multisession$storeFields(state);
        return state;
    }

    @Override
    public PackState multisession$newPacks() {
        PackState state = new PackState();
        state.manager = this.multisession$createManager(state);
        state.packSource = EMPTY_SOURCE;
        state.packType = PackSource.SERVER;
        state.feedback = LOG_ONLY_FEEDBACK;
        return state;
    }

    @Override
    public void multisession$installPacks(PackState state) {
        PackState current = this.multisession$installed;
        if (current == state) {
            return;
        }

        this.multisession$storeFields(current);
        current.installed = false;
        this.manager = state.manager;
        this.packSource = state.packSource;
        this.packType = state.packType;
        this.packFeedback = state.feedback;
        this.pendingReload = state.pendingReload;
        state.installed = true;
        this.multisession$installed = state;
    }

    @Unique
    private void multisession$storeFields(PackState state) {
        state.packSource = this.packSource;
        state.packType = this.packType;
        state.feedback = this.packFeedback;
        state.pendingReload = this.pendingReload;
    }

    @Unique
    private ServerPackManager multisession$createManager(PackState state) {
        PackLoadFeedback feedback = new PackLoadFeedback() {
            @Override
            public void reportUpdate(UUID id, PackLoadFeedback.Update update) {
                multisession$feedbackOf(state).reportUpdate(id, update);
            }

            @Override
            public void reportFinalResult(UUID id, PackLoadFeedback.FinalResult result) {
                multisession$feedbackOf(state).reportFinalResult(id, result);
            }
        };
        return new ServerPackManager(
                this.multisession$downloader,
                feedback,
                this.multisession$reloadConfig,
                this.multisession$updateRequest,
                ServerPackManager.PackPromptStatus.PENDING);
    }

    @Unique
    private PackLoadFeedback multisession$feedbackOf(PackState state) {
        return state.installed ? this.packFeedback : state.feedback;
    }
}
