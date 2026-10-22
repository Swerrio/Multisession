package io.swerr.multisession.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.swerr.multisession.mixin.JoinMultiplayerScreenAccessor;
import io.swerr.multisession.session.SessionManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class MultisessionClient implements ClientModInitializer {

    public static final String MOD_ID = "multisession";

    private static KeyMapping nextSession;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        nextSession = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "multisession.key.next",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (nextSession.consumeClick()) {
                SessionWarp.cycle(client);
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (offersSessions(client, screen)) {
                int sessions = sessionCount(client);
                Screens.getWidgets(screen).add(Button.builder(
                                sessions > 0
                                        ? Component.translatable("multisession.button.sessions", sessions)
                                        : Component.translatable("multisession.screen.title"),
                                button -> client.gui.setScreen(new SessionsScreen(screen)))
                        .bounds(4, 4, 100, 20)
                        .build());
            }
            if (screen instanceof JoinMultiplayerScreen multiplayer) {
                Button pin = Button.builder(Component.empty(), button -> {
                            ServerData selected = selectedServer(multiplayer);
                            if (selected != null) {
                                MultisessionConfig.togglePin(selected.name, selected.ip);
                            }
                        })
                        .bounds(width - 124, 4, 120, 20)
                        .build();
                Screens.getWidgets(screen).add(pin);
                updatePinButton(pin, selectedServer(multiplayer));
                ScreenEvents.afterTick(screen).register(tickedScreen -> updatePinButton(pin, selectedServer(multiplayer)));
            }
        });
    }

    private static @Nullable ServerData selectedServer(JoinMultiplayerScreen screen) {
        ServerSelectionList list = ((JoinMultiplayerScreenAccessor) screen).multisession$serverSelectionList();
        return list != null && list.getSelected() instanceof ServerSelectionList.OnlineServerEntry entry ? entry.getServerData() : null;
    }

    private static void updatePinButton(Button button, @Nullable ServerData selected) {
        boolean pinned = selected != null && MultisessionConfig.isPinned(selected.ip);
        button.active = selected != null;
        button.setMessage(Component.translatable(pinned ? "multisession.button.pinned" : "multisession.button.pin"));
        button.setTooltip(Tooltip.create(Component.translatable(pinned ? "multisession.unpin" : "multisession.pin")));
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
