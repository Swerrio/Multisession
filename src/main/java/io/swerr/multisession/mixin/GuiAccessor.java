package io.swerr.multisession.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.multiplayer.chat.ChatListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiAccessor {

    @Mutable
    @Accessor("hud")
    void multisession$setHud(Hud hud);

    @Mutable
    @Accessor("toastManager")
    void multisession$setToastManager(ToastManager toastManager);

    @Mutable
    @Accessor("chatListener")
    void multisession$setChatListener(ChatListener chatListener);
}
