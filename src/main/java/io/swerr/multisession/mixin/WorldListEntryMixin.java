package io.swerr.multisession.mixin;

import io.swerr.multisession.session.ClientSession;
import io.swerr.multisession.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.WorldSelectionList$WorldListEntry")
public abstract class WorldListEntryMixin {

    @Shadow
    @Final
    private LevelSummary summary;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "joinWorld", at = @At("HEAD"), cancellable = true)
    private void multisession$switchToParkedWorld(CallbackInfo ci) {
        ClientSession session = SessionManager.parkedWorld(this.summary.getLevelId());
        if (session != null) {
            SessionManager.activate(this.minecraft, session);
            ci.cancel();
        }
    }
}
