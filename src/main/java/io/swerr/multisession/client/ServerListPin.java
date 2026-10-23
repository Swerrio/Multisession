package io.swerr.multisession.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class ServerListPin {

    private static final Component PIN = Component.translatable("multisession.pin");
    private static final Component UNPIN = Component.translatable("multisession.unpin");
    private static final int PINNED_COLOR = 0xFFFFC940;
    private static final int PINNED_HOVER_COLOR = 0xFFFFE89A;
    private static final int UNPINNED_COLOR = 0xFF808080;
    private static final int UNPINNED_HOVER_COLOR = 0xFFFFFFFF;

    private ServerListPin() {
    }

    public static void extract(GuiGraphicsExtractor graphics, Font font, ServerData server, int nameX, int y,
            int mouseX, int mouseY, boolean active) {
        boolean pinned = MultisessionConfig.isPinned(server.ip);
        if (!pinned && !active) {
            return;
        }
        String star = pinned ? "★" : "☆";
        int x = x(font, server, nameX);
        boolean hovered = over(font, star, x, y, mouseX, mouseY);
        int color = pinned
                ? hovered ? PINNED_HOVER_COLOR : PINNED_COLOR
                : hovered ? UNPINNED_HOVER_COLOR : UNPINNED_COLOR;
        graphics.text(font, star, x, y, color);
        if (hovered) {
            graphics.setTooltipForNextFrame(pinned ? UNPIN : PIN, mouseX, mouseY);
        }
    }

    public static boolean click(Font font, ServerData server, int nameX, int y, double mouseX, double mouseY) {
        String star = MultisessionConfig.isPinned(server.ip) ? "★" : "☆";
        if (!over(font, star, x(font, server, nameX), y, mouseX, mouseY)) {
            return false;
        }
        MultisessionConfig.togglePin(server.name, server.ip);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }

    private static int x(Font font, ServerData server, int nameX) {
        return nameX + font.width(server.name) + 4;
    }

    private static boolean over(Font font, String star, int x, int y, double mouseX, double mouseY) {
        return mouseX >= x - 1 && mouseX < x + font.width(star) + 1 && mouseY >= y - 1 && mouseY < y + font.lineHeight;
    }
}
