package io.swerr.multisession.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.swerr.multisession.client.SessionWarp;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float multisession$switchEffectFov(float fov) {
        return SessionWarp.fov(minecraft, fov);
    }
}
