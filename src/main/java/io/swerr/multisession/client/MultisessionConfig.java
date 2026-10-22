package io.swerr.multisession.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class MultisessionConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("multisession.json");

    private static final Component HIDDEN_ADDRESS = Component.translatable("selectServer.hiddenAddress");
    private static final Map<Option, Boolean> OPTIONS = new EnumMap<>(Option.class);
    private static @Nullable List<Server> pinned;

    private MultisessionConfig() {
    }

    public static boolean get(Option option) {
        loaded();
        return OPTIONS.getOrDefault(option, option.defaultValue);
    }

    public static void set(Option option, boolean value) {
        if (get(option) != value) {
            OPTIONS.put(option, value);
            save();
        }
    }

    public static boolean hidesAddresses() {
        return Minecraft.getInstance().options.hideServerAddress;
    }

    public static void setHidesAddresses(boolean hidden) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideServerAddress != hidden) {
            minecraft.options.hideServerAddress = hidden;
            minecraft.options.save();
        }
    }

    public static Component displayName(String name, @Nullable String address) {
        if (hidesAddresses() && address != null && (name.equals(address) || sameAddress(name, address))) {
            return HIDDEN_ADDRESS;
        }
        return Component.literal(name);
    }

    public static List<Server> pinned() {
        return List.copyOf(loaded());
    }

    public static boolean isPinned(String address) {
        return find(address) != null;
    }

    public static void togglePin(String name, String address) {
        Server server = find(address);
        if (server != null) {
            loaded().remove(server);
        } else {
            loaded().add(new Server(name.isBlank() ? address.trim() : name, address.trim()));
        }
        save();
    }

    public static void unpin(Server server) {
        if (loaded().remove(server)) {
            save();
        }
    }

    public static boolean sameAddress(String first, String second) {
        return key(first).equals(key(second));
    }

    private static @Nullable Server find(String address) {
        for (Server server : loaded()) {
            if (sameAddress(server.address(), address)) {
                return server;
            }
        }
        return null;
    }

    private static String key(String address) {
        ServerAddress parsed = ServerAddress.parseString(address.trim());
        return parsed.getHost().toLowerCase(Locale.ROOT) + ":" + parsed.getPort();
    }

    private static List<Server> loaded() {
        if (pinned == null) {
            pinned = new ArrayList<>();
            load();
        }
        return pinned;
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject json = root.getAsJsonObject();
            for (Option option : Option.values()) {
                JsonElement value = json.get(option.key);
                if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
                    OPTIONS.put(option, value.getAsBoolean());
                }
            }
            if (json.get("pinned") instanceof JsonArray servers) {
                for (JsonElement element : servers) {
                    Server server = GSON.fromJson(element, Server.class);
                    if (server != null && server.address() != null && !server.address().isBlank()) {
                        pinned.add(new Server(server.name() != null ? server.name() : server.address(), server.address()));
                    }
                }
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.warn("Could not read Multisession settings from {}", FILE, e);
        }
    }

    private static void save() {
        JsonObject json = new JsonObject();
        for (Option option : Option.values()) {
            json.addProperty(option.key, get(option));
        }
        json.add("pinned", GSON.toJsonTree(loaded()));
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(json));
        } catch (IOException e) {
            LOGGER.warn("Could not save Multisession settings to {}", FILE, e);
        }
    }

    public enum Option {
        WARP_ANIMATION("warpAnimation", true),
        WARP_SOUND("warpSound", true),
        DISCONNECT_TOASTS("disconnectToasts", true),
        CONFIRM_CLOSE("confirmClose", true),
        TITLE_BUTTON("titleButton", false),
        NEW_SESSION("newSession", true);

        private final String key;
        private final boolean defaultValue;

        Option(String key, boolean defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }

        public String key() {
            return key;
        }
    }

    public record Server(String name, String address) {
    }
}
