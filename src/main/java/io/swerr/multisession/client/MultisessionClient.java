package io.swerr.multisession.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.swerr.multisession.session.SessionManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class MultisessionClient implements ClientModInitializer {

    public static final String MOD_ID = "multisession";

    private static KeyMapping nextSession;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        nextSession = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "multisession.key.next",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F6,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (nextSession.consumeClick()) {
                SessionWarp.cycle(client);
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (offersSessions(client, screen)) {
                int sessions = sessionCount(client);
                Button sessionsButton = Button.builder(
                                sessions > 0
                                        ? Component.translatable("multisession.button.sessions", sessions)
                                        : Component.translatable("multisession.screen.title"),
                                button -> client.gui.setScreen(new SessionsScreen(screen)))
                        .bounds(4, 4, 100, 20)
                        .build();
                Screens.getWidgets(screen).add(sessionsButton);
                keepClear(screen, sessionsButton);
                ScreenEvents.afterTick(screen).register(tickedScreen -> keepClear(tickedScreen, sessionsButton));
            }
        });
    }

    private static void keepClear(Screen screen, AbstractWidget widget) {
        widget.setX(4);
        for (int moves = 0; moves < 8; moves++) {
            AbstractWidget blocker = blocker(screen, widget);
            if (blocker == null) {
                return;
            }
            int next = blocker.getRight() + 4;
            if (next + widget.getWidth() > screen.width) {
                break;
            }
            widget.setX(next);
        }
        widget.setX(4);
    }

    private static @Nullable AbstractWidget blocker(Screen screen, AbstractWidget widget) {
        for (GuiEventListener child : screen.children()) {
            if (child != widget && child instanceof AbstractWidget other && other.visible
                    && other.getX() < widget.getRight() && widget.getX() < other.getRight()
                    && other.getY() < widget.getBottom() && widget.getY() < other.getBottom()) {
                return other;
            }
        }
        return null;
    }

    private static boolean offersSessions(Minecraft client, Screen screen) {
        if (screen instanceof PauseScreen) {
            return SessionManager.canPark(client);
        }
        if (screen instanceof TitleScreen && MultisessionConfig.get(MultisessionConfig.Option.TITLE_BUTTON)) {
            return true;
        }
        return SessionManager.hasParked()
                && (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen || screen instanceof SelectWorldScreen
                        || screen instanceof DisconnectedScreen);
    }

    private static int sessionCount(Minecraft client) {
        return SessionManager.parked().size() + (client.getConnection() != null ? 1 : 0);
    }
}
