package io.swerr.multisession.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.swerr.multisession.client.ServerListPin;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class OnlineServerEntryMixin extends ServerSelectionList.Entry {

    @Shadow
    @Final
    private JoinMultiplayerScreen screen;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ServerData serverData;

    @Unique
    private int multisession$nameX;

    @Unique
    private int multisession$nameY;

    @Inject(method = "render", at = @At("TAIL"))
    private void multisession$renderPin(PoseStack pose, int index, int top, int left, int width, int height,
            int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        this.multisession$nameX = left + 35;
        this.multisession$nameY = top + 1;
        boolean selected = ((JoinMultiplayerScreenAccessor) this.screen).multisession$serverSelectionList().getSelected() == (Object) this;
        ServerListPin.extract(pose, this.minecraft.font, this.serverData, this.multisession$nameX, this.multisession$nameY,
                mouseX, mouseY, hovered || selected, this.screen::setToolTip);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void multisession$togglePin(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT
                && ServerListPin.click(this.minecraft.font, this.serverData, this.multisession$nameX, this.multisession$nameY, mouseX, mouseY)) {
            cir.setReturnValue(true);
        }
    }
}
