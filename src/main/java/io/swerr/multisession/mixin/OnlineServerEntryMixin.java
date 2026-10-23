package io.swerr.multisession.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.swerr.multisession.client.ServerListPin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Inject(method = "extractContent", at = @At("TAIL"))
    private void multisession$extractPin(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        boolean selected = ((JoinMultiplayerScreenAccessor) this.screen).multisession$serverSelectionList().getSelected() == (Object) this;
        ServerListPin.extract(graphics, this.minecraft.font, this.serverData, getContentX() + 35, getContentY() + 1,
                mouseX, mouseY, hovered || selected);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void multisession$togglePin(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && ServerListPin.click(this.minecraft.font, this.serverData, getContentX() + 35, getContentY() + 1, event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }
}
