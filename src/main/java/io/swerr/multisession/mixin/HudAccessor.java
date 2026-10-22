package io.swerr.multisession.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.resources.WaypointStyleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Hud.class)
public interface HudAccessor {

    @Accessor("waypointStyles")
    WaypointStyleManager multisession$waypointStyles();

    @Mutable
    @Accessor("waypointStyles")
    void multisession$setWaypointStyles(WaypointStyleManager waypointStyles);

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

    @Accessor("isHidden")
    void multisession$setHidden(boolean hidden);
}
