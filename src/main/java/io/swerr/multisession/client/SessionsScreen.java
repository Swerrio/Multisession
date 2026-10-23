package io.swerr.multisession.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.swerr.multisession.session.ClientSession;
import io.swerr.multisession.session.SessionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.TooltipAccessor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class SessionsScreen extends Screen {

    private static final Component TITLE = Component.translatable("multisession.screen.title");
    private static final List<Component> TABS = List.of(
            Component.translatable("multisession.tab.sessions"),
            Component.translatable("multisession.tab.quick"),
            Component.translatable("multisession.tab.address"),
            Component.translatable("multisession.tab.settings"));
    private static final Component PIN = Component.translatable("multisession.pin");
    private static final Component UNPIN = Component.translatable("multisession.unpin");
    private static final int WIDTH = 224;
    private static final int SMALL = 20;
    private static final int ROW = 24;
    private static int lastTab;

    private final Screen parent;
    private final List<Label> labels = new ArrayList<>();
    private List<ClientSession> shownSessions = List.of();
    private List<MultisessionConfig.Server> shownPins = List.of();
    private @Nullable String address;
    private @Nullable EditBox addressBox;
    private @Nullable Button connectButton;
    private @Nullable Button addressPinButton;
    private @Nullable MultiLineLabel hint;
    private int hintY;
    private int y;

    public SessionsScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (this.address == null) {
            this.address = this.minecraft.options.lastMpIp;
        }
        shownSessions = SessionManager.parked();
        shownPins = MultisessionConfig.pinned();
        this.labels.clear();
        this.hint = null;
        this.addressBox = null;
        this.connectButton = null;
        this.addressPinButton = null;

        int tabWidth = Math.min(110, (this.width - 8) / TABS.size());
        int tabsLeft = (this.width - tabWidth * TABS.size()) / 2;
        for (int i = 0; i < TABS.size(); i++) {
            int index = i;
            Button tab = this.addRenderableWidget(new Button(tabsLeft + i * tabWidth, 4, tabWidth, SMALL, TABS.get(i), button -> {
                lastTab = index;
                this.rebuildWidgets();
            }));
            tab.active = i != lastTab;
        }

        this.y = 36;
        switch (lastTab) {
            case 1 -> quickContent();
            case 2 -> addressContent();
            case 3 -> settingsContent();
            default -> sessionsContent();
        }
        this.addRenderableWidget(new Button(this.width / 2 - 100, this.height - 28, 200, SMALL, CommonComponents.GUI_DONE, button -> this.onClose()));
    }

    @Override
    public void tick() {
        if (this.addressBox != null) {
            this.addressBox.tick();
        }
        if (!SessionManager.parked().equals(shownSessions) || !MultisessionConfig.pinned().equals(shownPins)) {
            this.rebuildWidgets();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && this.addressBox != null && this.addressBox.isFocused() && this.connectButton != null && this.connectButton.active) {
            this.connectToAddress();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float a) {
        this.renderBackground(pose);
        for (Label label : this.labels) {
            if (label.centered()) {
                drawCenteredString(pose, this.font, label.text(), label.x(), label.y(), 0xFFFFFF);
            } else {
                drawString(pose, this.font, label.text(), label.x(), label.y(), 0xFFFFFF);
            }
        }
        if (this.hint != null) {
            this.hint.renderCentered(pose, this.width / 2, this.hintY);
        }
        super.render(pose, mouseX, mouseY, a);
        for (GuiEventListener child : this.children()) {
            if (child instanceof CycleButton<?> button && button.isHoveredOrFocused() && button.isMouseOver(mouseX, mouseY)
                    && child instanceof TooltipAccessor tooltip) {
                this.renderTooltip(pose, tooltip.getTooltip(), mouseX, mouseY);
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private void sessionsContent() {
        boolean canSwitch = SessionManager.canSwitch(this.minecraft);
        Component current = currentLabel();
        String currentAddress = QuickJoin.activeAddress(this.minecraft);
        if (currentAddress != null) {
            int textWidth = this.font.width(current);
            int left = this.width / 2 - (textWidth + 4 + SMALL) / 2;
            this.labels.add(new Label(current, left, this.y + 6, false));
            ServerData data = this.minecraft.getCurrentServer();
            this.addRenderableWidget(pinButton(data != null ? data.name : currentAddress, currentAddress, left + textWidth + 4, this.y));
        } else {
            this.labels.add(new Label(current, this.width / 2, this.y + 6, true));
        }
        this.y += ROW + 4;

        if (shownSessions.isEmpty()) {
            this.labels.add(new Label(Component.translatable("multisession.screen.none"), this.width / 2, this.y + 6, true));
            this.y += ROW;
        }
        int left = this.width / 2 - WIDTH / 2;
        for (ClientSession session : shownSessions) {
            Component label = session.isInWorld()
                    ? session.label()
                    : Component.translatable("multisession.screen.loading", session.label());
            Button open = this.addRenderableWidget(new Button(left, this.y, WIDTH - 2 * (SMALL + 4), SMALL, label,
                    button -> SessionWarp.switchTo(this.minecraft, session)));
            open.active = canSwitch;
            if (session.address() != null) {
                this.addRenderableWidget(pinButton(session.name(), session.address(), left + WIDTH - 2 * SMALL - 4, this.y));
            }
            this.addRenderableWidget(new Button(left + WIDTH - SMALL, this.y, SMALL, SMALL, Component.literal("✕"),
                    button -> this.closeSession(session), tooltip(Component.translatable("multisession.screen.close"))));
            this.y += ROW;
        }

        this.y += 8;
        int half = (WIDTH - 4) / 2;
        Button newServer = this.addRenderableWidget(new Button(left, this.y, half, SMALL, Component.translatable("multisession.screen.new_server"),
                button -> this.openNewSession(new JoinMultiplayerScreen(new TitleScreen()))));
        boolean replacesWorld = SessionManager.hasWorld(this.minecraft);
        Button newWorld = this.addRenderableWidget(new Button(left + half + 4, this.y, half, SMALL, Component.translatable("multisession.screen.new_world"),
                button -> this.openNewSession(new SelectWorldScreen(new TitleScreen())),
                replacesWorld ? tooltip(Component.translatable("multisession.screen.new_world.replaces")) : Button.NO_TOOLTIP));
        newServer.active = canSwitch;
        newWorld.active = canSwitch;
    }

    private void quickContent() {
        int left = this.width / 2 - WIDTH / 2;
        this.addRenderableWidget(optionButton(MultisessionConfig.Option.NEW_SESSION, CommonComponents.GUI_YES, CommonComponents.GUI_NO));
        this.y += 4;
        if (shownPins.isEmpty()) {
            this.hint = MultiLineLabel.create(this.font, Component.translatable("multisession.quick.none"), WIDTH);
            this.hintY = this.y + 4;
        }
        for (MultisessionConfig.Server server : shownPins) {
            Component name = MultisessionConfig.displayName(server.name(), server.address());
            Button join = this.addRenderableWidget(new Button(left, this.y, WIDTH - SMALL - 4, SMALL,
                    QuickJoin.isOpen(this.minecraft, server.address()) ? Component.translatable("multisession.quick.open", name) : name,
                    button -> QuickJoin.join(this.minecraft, server.name(), server.address()),
                    MultisessionConfig.hidesAddresses() ? Button.NO_TOOLTIP : tooltip(Component.literal(server.address()))));
            join.active = QuickJoin.canJoin(this.minecraft, server.address());
            this.addRenderableWidget(new Button(left + WIDTH - SMALL, this.y, SMALL, SMALL, Component.literal("✕"), button -> {
                MultisessionConfig.unpin(server);
                this.rebuildWidgets();
            }, tooltip(UNPIN)));
            this.y += ROW;
        }
    }

    private void addressContent() {
        int left = this.width / 2 - WIDTH / 2;
        this.addRenderableWidget(optionButton(MultisessionConfig.Option.NEW_SESSION, CommonComponents.GUI_YES, CommonComponents.GUI_NO));
        this.y += 4;
        this.labels.add(new Label(Component.translatable("multisession.address.label"), this.width / 2, this.y + 6, true));
        this.y += ROW - 4;
        this.addressBox = this.addRenderableWidget(new EditBox(this.font, left, this.y, WIDTH, SMALL, Component.translatable("multisession.address.label")));
        this.addressBox.setMaxLength(256);
        if (MultisessionConfig.hidesAddresses()) {
            this.addressBox.setFormatter((text, offset) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        }
        this.addressBox.setValue(this.address);
        this.addressBox.setResponder(value -> {
            this.address = value;
            this.updateAddressButtons();
        });
        this.y += ROW;

        this.connectButton = this.addRenderableWidget(new Button(left, this.y, WIDTH - SMALL - 4, SMALL,
                Component.translatable("multisession.address.connect"), button -> this.connectToAddress()));
        this.addressPinButton = this.addRenderableWidget(new Button(left + WIDTH - SMALL, this.y, SMALL, SMALL, Component.literal("☆"), button -> {
            MultisessionConfig.togglePin(this.address.trim(), this.address.trim());
            this.rebuildWidgets();
        }, (button, pose, mouseX, mouseY) -> {
            boolean pinned = MultisessionConfig.isPinned(this.address.trim());
            this.renderTooltip(pose, this.font.split(pinned ? UNPIN : PIN, 200), mouseX, mouseY);
        }));
        this.y += ROW;
        this.updateAddressButtons();
    }

    private void updateAddressButtons() {
        if (this.connectButton == null || this.addressPinButton == null) {
            return;
        }
        String value = this.address.trim();
        boolean valid = ServerAddress.isValidAddress(value);
        boolean pinned = valid && MultisessionConfig.isPinned(value);
        this.connectButton.active = valid && QuickJoin.canJoin(this.minecraft, value);
        this.addressPinButton.active = valid;
        this.addressPinButton.setMessage(Component.literal(pinned ? "★" : "☆"));
    }

    private void connectToAddress() {
        String value = this.address.trim();
        this.minecraft.options.lastMpIp = value;
        this.minecraft.options.save();
        QuickJoin.join(this.minecraft, value, value);
    }

    private void settingsContent() {
        this.addRenderableWidget(optionButton(MultisessionConfig.Option.WARP_ANIMATION, CommonComponents.OPTION_ON, CommonComponents.OPTION_OFF));
        this.addRenderableWidget(optionButton(MultisessionConfig.Option.WARP_SOUND, CommonComponents.OPTION_ON, CommonComponents.OPTION_OFF));
        this.addRenderableWidget(optionButton(MultisessionConfig.Option.DISCONNECT_TOASTS, CommonComponents.OPTION_ON, CommonComponents.OPTION_OFF));
        this.addRenderableWidget(optionButton(MultisessionConfig.Option.CONFIRM_CLOSE, CommonComponents.OPTION_ON, CommonComponents.OPTION_OFF));
        this.addRenderableWidget(optionButton(MultisessionConfig.Option.NEW_SESSION, CommonComponents.GUI_YES, CommonComponents.GUI_NO));
        this.addRenderableWidget(optionButton(MultisessionConfig.Option.TITLE_BUTTON,
                Component.translatable("multisession.option.titleButton.always"),
                Component.translatable("multisession.option.titleButton.with_sessions")));
        this.addRenderableWidget(toggle("hideAddresses", MultisessionConfig.hidesAddresses(), CommonComponents.OPTION_ON, CommonComponents.OPTION_OFF,
                MultisessionConfig::setHidesAddresses));
    }

    private CycleButton<Boolean> optionButton(MultisessionConfig.Option option, Component on, Component off) {
        return toggle(option.key(), MultisessionConfig.get(option), on, off, value -> MultisessionConfig.set(option, value));
    }

    private CycleButton<Boolean> toggle(String name, boolean current, Component on, Component off, Consumer<Boolean> change) {
        String key = "multisession.option." + name;
        List<FormattedCharSequence> tooltip = this.font.split(Component.translatable(key + ".tooltip"), 200);
        CycleButton<Boolean> button = CycleButton.booleanBuilder(on, off)
                .withInitialValue(current)
                .withTooltip(value -> tooltip)
                .create(this.width / 2 - WIDTH / 2, this.y, WIDTH, SMALL, Component.translatable(key), (cycle, value) -> {
                    change.accept(value);
                    this.rebuildWidgets();
                });
        this.y += ROW;
        return button;
    }

    private void closeSession(ClientSession session) {
        if (!MultisessionConfig.get(MultisessionConfig.Option.CONFIRM_CLOSE)) {
            SessionManager.close(this.minecraft, session);
            this.rebuildWidgets();
            return;
        }
        this.minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        SessionManager.close(this.minecraft, session);
                    }
                    this.minecraft.setScreen(this);
                },
                Component.translatable("multisession.close.title", session.label()),
                Component.translatable("multisession.close.message")));
    }

    private Button pinButton(String name, String serverAddress, int x, int buttonY) {
        boolean pinned = MultisessionConfig.isPinned(serverAddress);
        return new Button(x, buttonY, SMALL, SMALL, Component.literal(pinned ? "★" : "☆"), button -> {
            MultisessionConfig.togglePin(name, serverAddress);
            this.rebuildWidgets();
        }, tooltip(pinned ? UNPIN : PIN));
    }

    private Button.OnTooltip tooltip(Component text) {
        return (button, pose, mouseX, mouseY) -> this.renderTooltip(pose, this.font.split(text, 200), mouseX, mouseY);
    }

    private void openNewSession(Screen next) {
        if (this.minecraft.getConnection() != null) {
            SessionManager.startNewSession(this.minecraft, next);
        } else {
            this.minecraft.setScreen(next);
        }
    }

    private Component currentLabel() {
        ClientPacketListener connection = this.minecraft.getConnection();
        if (connection == null) {
            return Component.translatable("multisession.screen.current.none");
        }
        ServerData data = this.minecraft.getCurrentServer();
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        Component name;
        if (server != null) {
            name = Component.literal(server.getWorldData().getLevelName());
        } else if (data != null) {
            name = MultisessionConfig.displayName(data.name, data.ip);
        } else {
            String remote = String.valueOf(connection.getConnection().getRemoteAddress());
            name = MultisessionConfig.displayName(remote, remote);
        }
        Component session = Component.translatable("multisession.session.label", name, connection.getLocalGameProfile().getName());
        return Component.translatable("multisession.screen.current", session);
    }

    private record Label(Component text, int x, int y, boolean centered) {
    }
}
