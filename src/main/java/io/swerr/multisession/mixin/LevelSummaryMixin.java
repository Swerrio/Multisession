package io.swerr.multisession.mixin;

import io.swerr.multisession.session.SessionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelSummary.class)
public abstract class LevelSummaryMixin {

    @Inject(method = "isDisabled", at = @At("HEAD"), cancellable = true)
    private void multisession$playParkedWorld(CallbackInfoReturnable<Boolean> cir) {
        if (SessionManager.parkedWorld(((LevelSummary) (Object) this).getLevelId()) != null) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getInfo", at = @At("HEAD"), cancellable = true)
    private void multisession$describeParkedWorld(CallbackInfoReturnable<Component> cir) {
        if (SessionManager.parkedWorld(((LevelSummary) (Object) this).getLevelId()) != null) {
            cir.setReturnValue(Component.translatable("multisession.world.parked").withStyle(ChatFormatting.GREEN));
        }
    }
}
