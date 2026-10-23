package io.swerr.multisession.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.swerr.multisession.session.SessionManager;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class MultisessionClient implements ClientModInitializer {

    public static final String MOD_ID = "multisession";

    private static final Map<Screen, Button> SESSIONS_BUTTONS = new WeakHashMap<>();

    private static KeyMapping nextSession;

    @Override
    public void onInitializeClient() {
        nextSession = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "multisession.key.next",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "key.category.multisession.main"));

        CoreShaderRegistrationCallback.EVENT.register(SessionWarp::registerShader);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (nextSession.consumeClick()) {
                SessionWarp.cycle(client);
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            boolean offered = offersSessions(client, screen);
            Button sessionsButton = SESSIONS_BUTTONS.get(screen);
            if (sessionsButton == null || !screen.children().contains(sessionsButton)) {
                if (!offered) {
                    return;
                }
                sessionsButton = new Button(4, 4, 100, 20, Component.empty(), button -> client.setScreen(new SessionsScreen(screen)));
                Screens.getButtons(screen).add(sessionsButton);
                SESSIONS_BUTTONS.put(screen, sessionsButton);
            }
            int sessions = sessionCount(client);
            sessionsButton.setMessage(sessions > 0
                    ? Component.translatable("multisession.button.sessions", sessions)
                    : Component.translatable("multisession.screen.title"));
            sessionsButton.visible = offered;
            Button shown = sessionsButton;
            keepClear(screen, shown);
            ScreenEvents.afterTick(screen).register(tickedScreen -> keepClear(tickedScreen, shown));
        });
    }

    private static void keepClear(Screen screen, AbstractWidget widget) {
        widget.x = 4;
        for (int moves = 0; moves < 8; moves++) {
            AbstractWidget blocker = blocker(screen, widget);
            if (blocker == null) {
                return;
            }
            int next = blocker.x + blocker.getWidth() + 4;
            if (next + widget.getWidth() > screen.width) {
                break;
            }
            widget.x = next;
        }
        widget.x = 4;
    }

    private static @Nullable AbstractWidget blocker(Screen screen, AbstractWidget widget) {
        for (GuiEventListener child : screen.children()) {
            if (child != widget && child instanceof AbstractWidget other && other.visible
                    && other.x < widget.x + widget.getWidth() && widget.x < other.x + other.getWidth()
                    && other.y < widget.y + widget.getHeight() && widget.y < other.y + other.getHeight()) {
                return other;
            }
        }
        return null;
    }

    private static boolean offersSessions(Minecraft client, Screen screen) {
        if (screen instanceof PauseScreen) {
            return SessionManager.canPark(client) || SessionManager.hasParked();
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
