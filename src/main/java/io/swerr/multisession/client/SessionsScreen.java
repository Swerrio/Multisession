package io.swerr.multisession.client;

import io.swerr.multisession.session.ClientSession;
import io.swerr.multisession.session.SessionManager;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

public class SessionsScreen extends Screen {

    private static final Component TITLE = Component.translatable("multisession.screen.title");
    private static final Component SESSIONS_TAB = Component.translatable("multisession.tab.sessions");
    private static final Component QUICK_TAB = Component.translatable("multisession.tab.quick");
    private static final Component ADDRESS_TAB = Component.translatable("multisession.tab.address");
    private static final Component SETTINGS_TAB = Component.translatable("multisession.tab.settings");
    private static final Component PIN = Component.translatable("multisession.pin");
    private static final Component UNPIN = Component.translatable("multisession.unpin");
    private static final int WIDTH = 224;
    private static final int SMALL = 20;
    private static int lastTab;

    private final Screen parent;
    private HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final TabManager tabManager = new TabManager(widget -> this.addRenderableWidget(widget), widget -> this.removeWidget(widget));
    private @Nullable TabNavigationBar tabBar;
    private List<ClientSession> shownSessions = List.of();
    private List<MultisessionConfig.Server> shownPins = List.of();
    private @Nullable String address;
    private @Nullable EditBox addressBox;
    private @Nullable Button connectButton;
    private @Nullable Button addressPinButton;

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

        this.layout = new HeaderAndFooterLayout(this);
        this.tabBar = TabNavigationBar.builder(this.tabManager, this.width)
                .addTabs(
                        new ScrollTab(SESSIONS_TAB, sessionsContent()),
                        new ScrollTab(QUICK_TAB, quickContent()),
                        new ScrollTab(ADDRESS_TAB, addressContent()),
                        new ScrollTab(SETTINGS_TAB, settingsContent()))
                .build();
        this.addRenderableWidget(this.tabBar);
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(200).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.tabBar.selectTab(lastTab, false);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (this.tabBar == null) {
            return;
        }
        this.tabBar.updateWidth(this.width);
        this.tabBar.arrangeElements();
        int top = this.tabBar.getRectangle().bottom();
        this.tabManager.setTabArea(new ScreenRectangle(0, top, this.width, this.height - this.layout.getFooterHeight() - top));
        this.layout.setHeaderHeight(top);
        this.layout.arrangeElements();
    }

    @Override
    public void tick() {
        rememberTab();
        if (!SessionManager.parked().equals(shownSessions) || !MultisessionConfig.pinned().equals(shownPins)) {
            this.rebuildWidgets();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.tabBar != null && this.tabBar.keyPressed(event)) {
            return true;
        }
        if (event.isConfirmation() && this.addressBox != null && this.addressBox.isFocused() && this.connectButton != null && this.connectButton.active) {
            this.connectToAddress();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.FOOTER_SEPARATOR, 0, this.height - this.layout.getFooterHeight(), 0.0F, 0.0F, this.width, 2, 32, 2);
    }

    @Override
    protected void extractMenuBackground(GuiGraphicsExtractor graphics) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, CreateWorldScreen.TAB_HEADER_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.layout.getHeaderHeight(), 16, 16);
        this.extractMenuBackground(graphics, 0, this.layout.getHeaderHeight(), this.width, this.height);
    }

    @Override
    public void onClose() {
        rememberTab();
        this.minecraft.setScreen(parent);
    }

    @Override
    protected void rebuildWidgets() {
        rememberTab();
        super.rebuildWidgets();
    }

    private LinearLayout sessionsContent() {
        boolean canSwitch = SessionManager.canSwitch(this.minecraft);
        LinearLayout content = column();

        LinearLayout current = LinearLayout.horizontal().spacing(4);
        current.defaultCellSetting().alignVerticallyMiddle();
        current.addChild(new StringWidget(currentLabel(), this.font));
        String currentAddress = QuickJoin.activeAddress(this.minecraft);
        if (currentAddress != null) {
            ServerData data = this.minecraft.getConnection().getServerData();
            current.addChild(pinButton(data != null ? data.name : currentAddress, currentAddress));
        }
        content.addChild(current, settings -> settings.paddingBottom(4));

        if (shownSessions.isEmpty()) {
            content.addChild(new StringWidget(Component.translatable("multisession.screen.none"), this.font));
        }
        for (ClientSession session : shownSessions) {
            LinearLayout row = LinearLayout.horizontal().spacing(4);
            Component label = session.isInWorld()
                    ? session.label()
                    : Component.translatable("multisession.screen.loading", session.label());
            Button open = row.addChild(Button.builder(label, button -> SessionWarp.switchTo(this.minecraft, session))
                    .width(WIDTH - 2 * (SMALL + 4))
                    .build());
            open.active = canSwitch;
            if (session.address() != null) {
                row.addChild(pinButton(session.name(), session.address()));
            } else {
                row.addChild(SpacerElement.width(SMALL));
            }
            row.addChild(Button.builder(Component.literal("✕"), button -> this.closeSession(session))
                    .width(SMALL)
                    .tooltip(Tooltip.create(Component.translatable("multisession.screen.close")))
                    .build());
            content.addChild(row);
        }

        LinearLayout newSessions = LinearLayout.horizontal().spacing(4);
        Button newServer = newSessions.addChild(Button.builder(Component.translatable("multisession.screen.new_server"),
                        button -> this.openNewSession(new JoinMultiplayerScreen(new TitleScreen())))
                .width((WIDTH - 4) / 2)
                .build());
        Button newWorld = newSessions.addChild(Button.builder(Component.translatable("multisession.screen.new_world"),
                        button -> this.openNewSession(new SelectWorldScreen(new TitleScreen())))
                .width((WIDTH - 4) / 2)
                .build());
        newServer.active = canSwitch;
        newWorld.active = canSwitch;
        if (SessionManager.hasWorld(this.minecraft)) {
            newWorld.setTooltip(Tooltip.create(Component.translatable("multisession.screen.new_world.replaces")));
        }
        content.addChild(newSessions, settings -> settings.paddingTop(8));
        return content;
    }

    private LinearLayout quickContent() {
        LinearLayout content = column();
        content.addChild(optionButton(MultisessionConfig.Option.NEW_SESSION, CommonComponents.GUI_YES, CommonComponents.GUI_NO),
                settings -> settings.paddingBottom(4));
        if (shownPins.isEmpty()) {
            content.addChild(new MultiLineTextWidget(Component.translatable("multisession.quick.none"), this.font)
                    .setMaxWidth(WIDTH)
                    .setCentered(true));
        }
        for (MultisessionConfig.Server server : shownPins) {
            LinearLayout row = LinearLayout.horizontal().spacing(4);
            Component name = MultisessionConfig.displayName(server.name(), server.address());
            Button join = row.addChild(Button.builder(
                            QuickJoin.isOpen(this.minecraft, server.address()) ? Component.translatable("multisession.quick.open", name) : name,
                            button -> QuickJoin.join(this.minecraft, server.name(), server.address()))
                    .width(WIDTH - SMALL - 4)
                    .tooltip(MultisessionConfig.hidesAddresses() ? null : Tooltip.create(Component.literal(server.address())))
                    .build());
            join.active = QuickJoin.canJoin(this.minecraft, server.address());
            row.addChild(Button.builder(Component.literal("✕"), button -> {
                        MultisessionConfig.unpin(server);
                        this.rebuildWidgets();
                    })
                    .width(SMALL)
                    .tooltip(Tooltip.create(UNPIN))
                    .build());
            content.addChild(row);
        }
        return content;
    }

    private LinearLayout addressContent() {
        LinearLayout content = column();
        content.addChild(optionButton(MultisessionConfig.Option.NEW_SESSION, CommonComponents.GUI_YES, CommonComponents.GUI_NO),
                settings -> settings.paddingBottom(4));
        content.addChild(new StringWidget(Component.translatable("multisession.address.label"), this.font));
        this.addressBox = content.addChild(new EditBox(this.font, WIDTH, SMALL, Component.translatable("multisession.address.label")));
        this.addressBox.setMaxLength(256);
        if (MultisessionConfig.hidesAddresses()) {
            this.addressBox.addFormatter((text, offset) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        }
        this.addressBox.setValue(this.address);
        this.addressBox.setResponder(value -> {
            this.address = value;
            this.updateAddressButtons();
        });

        LinearLayout row = LinearLayout.horizontal().spacing(4);
        this.connectButton = row.addChild(Button.builder(Component.translatable("multisession.address.connect"), button -> this.connectToAddress())
                .width(WIDTH - SMALL - 4)
                .build());
        this.addressPinButton = row.addChild(Button.builder(Component.literal("☆"), button -> {
                    MultisessionConfig.togglePin(this.address.trim(), this.address.trim());
                    this.rebuildWidgets();
                })
                .width(SMALL)
                .build());
        content.addChild(row);
        this.updateAddressButtons();
        return content;
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
        this.addressPinButton.setTooltip(Tooltip.create(pinned ? UNPIN : PIN));
    }

    private void connectToAddress() {
        String value = this.address.trim();
        this.minecraft.options.lastMpIp = value;
        this.minecraft.options.save();
        QuickJoin.join(this.minecraft, value, value);
    }

    private LinearLayout settingsContent() {
        LinearLayout content = column();
        content.addChild(optionButton(MultisessionConfig.Option.WARP_ANIMATION, CommonComponents.OPTION_ON, CommonComponents.OPTION_OFF));
        content.addChild(optionButton(MultisessionConfig.Option.WARP_SOUND, CommonComponents.OPTION_ON, CommonComponents.OPTION_OFF));
        content.addChild(optionButton(MultisessionConfig.Option.DISCONNECT_TOASTS, CommonComponents.OPTION_ON, CommonComponents.OPTION_OFF));
        content.addChild(optionButton(MultisessionConfig.Option.CONFIRM_CLOSE, CommonComponents.OPTION_ON, CommonComponents.OPTION_OFF));
        content.addChild(optionButton(MultisessionConfig.Option.NEW_SESSION, CommonComponents.GUI_YES, CommonComponents.GUI_NO));
        content.addChild(optionButton(MultisessionConfig.Option.TITLE_BUTTON,
                Component.translatable("multisession.option.titleButton.always"),
                Component.translatable("multisession.option.titleButton.with_sessions")));
        content.addChild(toggle("hideAddresses", MultisessionConfig.hidesAddresses(), CommonComponents.OPTION_ON, CommonComponents.OPTION_OFF,
                MultisessionConfig::setHidesAddresses));
        return content;
    }

    private CycleButton<Boolean> optionButton(MultisessionConfig.Option option, Component on, Component off) {
        return toggle(option.key(), MultisessionConfig.get(option), on, off, value -> MultisessionConfig.set(option, value));
    }

    private CycleButton<Boolean> toggle(String name, boolean current, Component on, Component off, Consumer<Boolean> change) {
        String key = "multisession.option." + name;
        return CycleButton.booleanBuilder(on, off, current)
                .withTooltip(value -> Tooltip.create(Component.translatable(key + ".tooltip")))
                .create(0, 0, WIDTH, SMALL, Component.translatable(key), (button, value) -> {
                    change.accept(value);
                    this.rebuildWidgets();
                });
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

    private Button pinButton(String name, String serverAddress) {
        boolean pinned = MultisessionConfig.isPinned(serverAddress);
        return Button.builder(Component.literal(pinned ? "★" : "☆"), button -> {
                    MultisessionConfig.togglePin(name, serverAddress);
                    this.rebuildWidgets();
                })
                .width(SMALL)
                .tooltip(Tooltip.create(pinned ? UNPIN : PIN))
                .build();
    }

    private LinearLayout column() {
        LinearLayout content = LinearLayout.vertical().spacing(4);
        content.defaultCellSetting().alignHorizontallyCenter();
        return content;
    }

    private void rememberTab() {
        if (this.tabBar != null) {
            int index = this.tabBar.getTabs().indexOf(this.tabManager.getCurrentTab());
            if (index >= 0) {
                lastTab = index;
            }
        }
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
        ServerData data = connection.getServerData();
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
        Component session = Component.translatable("multisession.session.label", name, connection.getLocalGameProfile().name());
        return Component.translatable("multisession.screen.current", session);
    }

    private final class ScrollTab implements Tab {

        private final Component title;
        private final ScrollableLayout scroll;

        private ScrollTab(Component title, Layout content) {
            this.title = title;
            content.arrangeElements();
            this.scroll = new ScrollableLayout(SessionsScreen.this.minecraft, content, 100);
        }

        @Override
        public Component getTabTitle() {
            return title;
        }

        @Override
        public Component getTabExtraNarration() {
            return Component.empty();
        }

        @Override
        public void visitChildren(Consumer<AbstractWidget> consumer) {
            scroll.visitWidgets(consumer);
        }

        @Override
        public void doLayout(ScreenRectangle area) {
            ScreenRectangle padded = new ScreenRectangle(area.left(), area.top() + 10, area.width(), Math.max(area.height() - 20, 20));
            scroll.setMaxHeight(padded.height());
            scroll.arrangeElements();
            FrameLayout.alignInRectangle(scroll, padded, 0.5F, 0.0F);
        }
    }
}
