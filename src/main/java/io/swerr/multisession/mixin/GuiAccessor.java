package io.swerr.multisession.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiAccessor {

    @Accessor("debugOverlay")
    DebugScreenOverlay multisession$debugOverlay();

    @Mutable
    @Accessor("debugOverlay")
    void multisession$setDebugOverlay(DebugScreenOverlay debugOverlay);

    @Accessor("subtitleOverlay")
    SubtitleOverlay multisession$subtitleOverlay();

    @Mutable
    @Accessor("subtitleOverlay")
    void multisession$setSubtitleOverlay(SubtitleOverlay subtitleOverlay);
}
