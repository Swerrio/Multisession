package io.swerr.multisession.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import io.swerr.multisession.session.ClientSession;
import io.swerr.multisession.session.LostSession;
import io.swerr.multisession.session.SessionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

final class SessionGrid extends AbstractWidget {

    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/button_highlighted");
    private static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");
    private static final Identifier FOOD_EMPTY = Identifier.withDefaultNamespace("hud/food_empty");
    private static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");
    private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
    private static final Identifier[] PING = {
            Identifier.withDefaultNamespace("icon/ping_unknown"),
            Identifier.withDefaultNamespace("icon/ping_1"),
            Identifier.withDefaultNamespace("icon/ping_2"),
            Identifier.withDefaultNamespace("icon/ping_3"),
            Identifier.withDefaultNamespace("icon/ping_4"),
            Identifier.withDefaultNamespace("icon/ping_5")
    };
    private static final SoundEvent CHARGE_SOUND = SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath(MultisessionClient.MOD_ID, "card.charge"));
    private static final SoundEvent CLOSE_SOUND = SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath(MultisessionClient.MOD_ID, "card.close"));

    private static final Component CURRENT = Component.translatable("multisession.card.current");
    private static final Component HURT = Component.translatable("multisession.card.hurt");
    private static final Component DEAD = Component.translatable("multisession.card.dead");
    private static final Component NOW = Component.translatable("multisession.card.now");
    private static final Component RECONFIGURING = Component.translatable("multisession.card.reconfiguring");
    private static final Component CONNECTING = Component.translatable("multisession.card.connecting");
    private static final Component SAVING = Component.translatable("multisession.card.saving");
    private static final Component RECONNECT = Component.translatable("multisession.card.reconnect");
    private static final Component HOLD = Component.translatable("multisession.card.hold");
    private static final Component NEW = Component.translatable("multisession.card.new");
    private static final Component NEW_SERVER = Component.translatable("multisession.card.new_server");
    private static final Component NEW_WORLD = Component.translatable("multisession.card.new_world");
    private static final Component WORLD_REPLACES = Component.translatable("multisession.screen.new_world.replaces");
    private static final Component PUBLISHED = Component.translatable("multisession.card.published");
    private static final Component PIN = Component.translatable("multisession.pin");
    private static final Component PING_UNKNOWN = Component.translatable("multisession.card.ping.unknown");
    private static final Component UNPIN = Component.translatable("multisession.unpin");

    private static final int GAP = 8;
    private static final int STRIP = 24;
    private static final int CONTROL = 14;
    private static final int BUTTON_HEIGHT = 20;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED = 0xFFB4B2A9;
    private static final int GOLD = 0xFFF2D36B;
    private static final long HOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(900);
    private static final long DRAIN_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final long COLLAPSE_NANOS = TimeUnit.MILLISECONDS.toNanos(180);
    private static final long TAP_NANOS = TimeUnit.MILLISECONDS.toNanos(180);
    private static final long HINT_NANOS = TimeUnit.MILLISECONDS.toNanos(1500);
    private static final long WIGGLE_NANOS = TimeUnit.MILLISECONDS.toNanos(300);
    private static final float IMPULSE = 6.0F;

    private final Minecraft minecraft;
    private final Font font;
    private final Runnable newServer;
    private final Runnable newWorld;
    private final Random random = new Random();

    private List<Placed> placed = List.of();
    private int scroll;
    private int contentHeight;
    private long lastFrame = System.nanoTime();

    private @Nullable Object holdKey;
    private boolean holding;
    private float holdProgress;
    private long holdStart;
    private @Nullable SoundInstance chargeSound;
    private @Nullable Object collapseKey;
    private long collapseStart;
    private long impulseStart;
    private @Nullable Object wiggleKey;
    private long wiggleStart;
    private long hintUntil;

    private @Nullable Placed pressed;
    private double pressX;
    private double pressY;
    private boolean dragging;
    private double dragX;
    private double dragY;

    SessionGrid(Minecraft minecraft, Runnable newServer, Runnable newWorld) {
        super(0, 0, 0, 0, Component.translatable("multisession.tab.sessions"));
        this.minecraft = minecraft;
        this.font = minecraft.font;
        this.newServer = newServer;
        this.newWorld = newWorld;
    }

    void tick() {
        long now = System.nanoTime();
        if (collapseKey != null && now - collapseStart >= COLLAPSE_NANOS) {
            Object key = collapseKey;
            collapseKey = null;
            close(key);
        }
    }

    boolean tracksMouse() {
        return holding || pressed != null;
    }

    float shake() {
        long now = System.nanoTime();
        float hold = holdKey != null ? 3.0F * holdProgress * holdProgress : 0.0F;
        float impulse = impulseStart != 0L ? IMPULSE * (float) Math.exp(-(now - impulseStart) / 1.2e8) : 0.0F;
        return Math.max(hold, impulse > 0.2F ? impulse : 0.0F);
    }

    boolean activateNumber(int index) {
        int live = 0;
        for (Placed card : placed) {
            if (card.card instanceof Live) {
                if (live == index) {
                    open(card);
                    return true;
                }
                live++;
            }
        }
        return false;
    }

    void stopSounds() {
        if (chargeSound != null) {
            minecraft.getSoundManager().stop(chargeSound);
            chargeSound = null;
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        long now = System.nanoTime();
        advance(now);
        List<Card> cards = cards();
        layout(cards);
        boolean scrolls = contentHeight > this.height;
        if (scrolls) {
            graphics.enableScissor(this.getX(), this.getY(), this.getRight(), this.getBottom());
        }
        Placed dragged = null;
        for (Placed card : placed) {
            if (dragging && pressed != null && card.key().equals(pressed.key())) {
                dragged = card;
                continue;
            }
            extractCard(graphics, card, card.x, card.y, mouseX, mouseY, now, false);
        }
        if (scrolls) {
            graphics.disableScissor();
        }
        if (dragged != null) {
            graphics.nextStratum();
            extractCard(graphics, dragged, (int) (dragX - (pressX - pressed.x)), (int) (dragY - (pressY - pressed.y)), mouseX, mouseY, now, true);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.isValidClickButton(event.buttonInfo()) || !this.isMouseOver(event.x(), event.y()) || collapseKey != null) {
            return false;
        }
        Placed card = at(event.x(), event.y());
        if (card == null) {
            return false;
        }
        long now = System.nanoTime();
        switch (hit(card, event.x(), event.y())) {
            case CLOSE -> {
                if (holdKey == null || !holdKey.equals(card.key())) {
                    holdProgress = 0.0F;
                }
                holdKey = card.key();
                holding = true;
                holdStart = now;
                stopSounds();
                chargeSound = SimpleSoundInstance.forUI(CHARGE_SOUND, 1.0F, 0.6F);
                minecraft.getSoundManager().play(chargeSound);
            }
            case PIN -> {
                Pin pin = pin(card.card);
                if (pin != null) {
                    MultisessionConfig.togglePin(pin.name, pin.address);
                    click();
                }
            }
            case RECONNECT -> {
                if (card.card instanceof Lost lost && QuickJoin.canReconnect(minecraft)) {
                    click();
                    QuickJoin.reconnect(minecraft, lost.session);
                }
            }
            case NEW_SERVER -> {
                if (SessionManager.canSwitch(minecraft)) {
                    click();
                    newServer.run();
                }
            }
            case NEW_WORLD -> {
                if (SessionManager.canSwitch(minecraft)) {
                    click();
                    newWorld.run();
                }
            }
            case BODY -> {
                pressed = card;
                pressX = event.x();
                pressY = event.y();
                dragX = event.x();
                dragY = event.y();
                dragging = false;
            }
            case NONE -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (pressed != null && pressed.card instanceof Live) {
            dragX = event.x();
            dragY = event.y();
            if (!dragging && Math.abs(dragX - pressX) + Math.abs(dragY - pressY) > 4.0) {
                dragging = true;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        long now = System.nanoTime();
        if (holdKey != null && holding) {
            release(now);
            return true;
        }
        Placed card = pressed;
        pressed = null;
        if (card == null) {
            return false;
        }
        if (dragging) {
            dragging = false;
            drop(card, event.x(), event.y());
            return true;
        }
        Placed under = at(event.x(), event.y());
        if (under != null && under.key().equals(card.key())) {
            open(card);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (contentHeight <= this.height) {
            return false;
        }
        scroll = Mth.clamp(scroll - (int) (scrollY * 24.0), 0, contentHeight - this.height);
        return true;
    }

    private void advance(long now) {
        float elapsed = Math.min(now - lastFrame, TimeUnit.MILLISECONDS.toNanos(100));
        lastFrame = now;
        if (holdKey == null) {
            return;
        }
        if (holding) {
            holdProgress = Math.min(1.0F, holdProgress + elapsed / HOLD_NANOS);
            if (holdProgress >= 1.0F) {
                stopSounds();
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(CLOSE_SOUND, 1.0F, 0.8F));
                collapseKey = holdKey;
                collapseStart = now;
                impulseStart = now;
                holdKey = null;
                holding = false;
                holdProgress = 0.0F;
            }
        } else {
            holdProgress = Math.max(0.0F, holdProgress - elapsed / DRAIN_NANOS);
            if (holdProgress <= 0.0F) {
                holdKey = null;
            }
        }
    }

    private void release(long now) {
        holding = false;
        stopSounds();
        if (now - holdStart < TAP_NANOS) {
            wiggleKey = holdKey;
            wiggleStart = now;
            hintUntil = now + HINT_NANOS;
        }
    }

    private void close(Object key) {
        for (Placed card : placed) {
            if (!card.key().equals(key)) {
                continue;
            }
            if (card.card instanceof Live live && live.parked != null) {
                if (live.parked.world() != null) {
                    SessionCards.closing(live.parked);
                }
                SessionManager.close(minecraft, live.parked);
            } else if (card.card instanceof Lost lost) {
                SessionManager.dismiss(minecraft, lost.session);
            }
            return;
        }
    }

    private void open(Placed card) {
        if (!(card.card instanceof Live live)) {
            return;
        }
        click();
        if (live.parked == null) {
            if (minecraft.level != null) {
                minecraft.gui.setScreen(null);
            }
            return;
        }
        if (SessionManager.canSwitch(minecraft)) {
            SessionWarp.switchTo(minecraft, live.parked);
        }
    }

    private void drop(Placed card, double x, double y) {
        if (!(card.card instanceof Live live)) {
            return;
        }
        int target = 0;
        double best = Double.MAX_VALUE;
        int index = 0;
        for (Placed slot : placed) {
            if (!(slot.card instanceof Live)) {
                continue;
            }
            double cx = slot.x + slot.width / 2.0;
            double cy = slot.y + slot.height / 2.0;
            double distance = (cx - x) * (cx - x) + (cy - y) * (cy - y);
            if (distance < best) {
                best = distance;
                target = index;
            }
            index++;
        }
        SessionCards.move(live.key, target);
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private List<Card> cards() {
        List<Card> cards = new ArrayList<>();
        for (Connection key : SessionCards.order(minecraft)) {
            cards.add(new Live(key, SessionCards.parked(key)));
        }
        if (dragging && pressed != null && pressed.card instanceof Live live) {
            int from = -1;
            for (int i = 0; i < cards.size(); i++) {
                if (((Live) cards.get(i)).key == live.key) {
                    from = i;
                }
            }
            if (from >= 0) {
                int to = slotAt(cards.size(), dragX, dragY);
                Card moved = cards.remove(from);
                cards.add(Mth.clamp(to, 0, cards.size()), moved);
            }
        }
        for (LostSession lost : SessionManager.lost()) {
            cards.add(new Lost(lost));
        }
        for (SessionCards.ClosingWorld world : SessionCards.closing()) {
            cards.add(new Closing(world));
        }
        cards.add(NewCard.INSTANCE);
        return cards;
    }

    private int slotAt(int liveCount, double x, double y) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        int index = 0;
        for (Placed slot : placed) {
            if (index >= liveCount) {
                break;
            }
            double cx = slot.x + slot.width / 2.0;
            double cy = slot.y + slot.height / 2.0;
            double distance = (cx - x) * (cx - x) + (cy - y) * (cy - y);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
            index++;
        }
        return best;
    }

    private void layout(List<Card> cards) {
        if (this.width <= GAP * 4 || this.height <= STRIP * 2) {
            placed = List.of();
            contentHeight = 0;
            return;
        }
        int count = cards.size();
        int columns = count <= 1 ? 1 : count <= 4 ? 2 : count <= 9 ? 3 : 4;
        int rows = (count + columns - 1) / columns;
        int maxWidth = count <= 1 ? 300 : count <= 2 ? 280 : 260;
        int cardWidth = Math.min(maxWidth, (this.width - GAP * (columns - 1)) / columns);
        int cardHeight = cardWidth * 9 / 16 + STRIP;
        int total = rows * cardHeight + (rows - 1) * GAP;
        if (total > this.height && count <= 12) {
            cardHeight = Math.max(STRIP + 36, (this.height - (rows - 1) * GAP) / rows);
            cardWidth = Math.min(cardWidth, (cardHeight - STRIP) * 16 / 9);
            total = rows * cardHeight + (rows - 1) * GAP;
        }
        contentHeight = total;
        scroll = Mth.clamp(scroll, 0, Math.max(0, total - this.height));
        int top = this.getY() + Math.max(0, (this.height - total) / 2) - scroll;

        List<Placed> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int row = i / columns;
            int inRow = Math.min(columns, count - row * columns);
            int left = this.getX() + (this.width - (inRow * cardWidth + (inRow - 1) * GAP)) / 2;
            int column = i % columns;
            result.add(new Placed(cards.get(i), left + column * (cardWidth + GAP), top + row * (cardHeight + GAP), cardWidth, cardHeight));
        }
        placed = result;
    }

    private @Nullable Placed at(double x, double y) {
        if (!this.isMouseOver(x, y)) {
            return null;
        }
        for (Placed card : placed) {
            if (x >= card.x && x < card.x + card.width && y >= card.y && y < card.y + card.height) {
                return card;
            }
        }
        return null;
    }

    private Hit hit(Placed card, double x, double y) {
        if (card.card instanceof NewCard) {
            int[] buttons = newButtons(card);
            if (y >= buttons[1] && y < buttons[1] + BUTTON_HEIGHT) {
                if (x >= buttons[0] && x < buttons[0] + buttons[2]) {
                    return Hit.NEW_SERVER;
                }
                if (x >= buttons[3] && x < buttons[3] + buttons[2]) {
                    return Hit.NEW_WORLD;
                }
            }
            return Hit.NONE;
        }
        if (inside(x, y, closeX(card), card.y + 4, CONTROL, CONTROL) && closable(card.card)) {
            return Hit.CLOSE;
        }
        if (inside(x, y, pinX(card), card.y + 4, CONTROL, CONTROL) && pin(card.card) != null) {
            return Hit.PIN;
        }
        if (card.card instanceof Lost lost) {
            int[] button = reconnectButton(card, lost);
            if (button != null && inside(x, y, button[0], button[1], button[2], BUTTON_HEIGHT)) {
                return Hit.RECONNECT;
            }
            return Hit.NONE;
        }
        return card.card instanceof Live ? Hit.BODY : Hit.NONE;
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static int closeX(Placed card) {
        return card.x + card.width - 4 - CONTROL;
    }

    private int pinX(Placed card) {
        return closable(card.card) ? closeX(card) - CONTROL - 2 : closeX(card);
    }

    private static boolean closable(Card card) {
        return card instanceof Live live && live.parked != null || card instanceof Lost;
    }

    private @Nullable Pin pin(Card card) {
        if (card instanceof Live live) {
            if (live.parked == null) {
                String address = QuickJoin.activeAddress(minecraft);
                ClientPacketListener connection = minecraft.getConnection();
                if (address == null || connection == null) {
                    return null;
                }
                return new Pin(connection.getServerData() != null ? connection.getServerData().name : address, address);
            }
            return live.parked.address() != null ? new Pin(live.parked.name(), live.parked.address()) : null;
        }
        return null;
    }

    private void extractCard(GuiGraphicsExtractor graphics, Placed card, int x, int y, int mouseX, int mouseY, long now, boolean lifted) {
        int width = card.width;
        int height = card.height;
        boolean hovered = !dragging && collapseKey == null && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
                && this.isMouseOver(mouseX, mouseY);
        float scale = lifted ? 1.05F : hovered && card.card instanceof Live ? 1.03F : 1.0F;
        boolean collapsing = collapseKey != null && collapseKey.equals(card.key());
        if (collapsing) {
            float t = Math.min(1.0F, (now - collapseStart) / (float) COLLAPSE_NANOS);
            scale = 1.0F - 0.35F * t * t;
        }
        graphics.pose().pushMatrix();
        float cx = x + width / 2.0F;
        float cy = y + height / 2.0F;
        graphics.pose().translate(cx, cy);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-cx, -cy);

        int pictureHeight = height - STRIP;
        switch (card.card) {
            case Live live -> extractLive(graphics, live, x, y, width, pictureHeight, mouseX, mouseY);
            case Lost lost -> extractLost(graphics, lost, x, y, width, pictureHeight, mouseX, mouseY);
            case Closing closing -> extractClosing(graphics, closing, x, y, width, pictureHeight);
            case NewCard ignored -> extractNew(graphics, card, x, y, width, height, mouseX, mouseY);
        }

        if (card.card instanceof Live live) {
            int border = live.parked == null ? GOLD : SessionCards.hurt(live.key)
                    ? Mth.sin(now / 1.5e8F) > 0.0F ? 0xFFE24B4A : 0xFF791F1F
                    : hovered || lifted ? WHITE : 0;
            if (border != 0) {
                graphics.outline(x - 1, y - 1, width + 2, height + 2, border);
                if (live.parked == null || border != WHITE) {
                    graphics.outline(x - 2, y - 2, width + 4, height + 4, border);
                }
            }
        } else if (card.card instanceof NewCard && hovered) {
            graphics.outline(x - 1, y - 1, width + 2, height + 2, 0xFF888780);
        }
        if (card.card instanceof Live live && live.parked != null && !SessionManager.canSwitch(minecraft)) {
            graphics.fill(x, y, x + width, y + height, 0x70000000);
            if (hovered && SessionManager.isPublishedWorld(minecraft)) {
                graphics.setTooltipForNextFrame(font, font.split(PUBLISHED, 200), mouseX, mouseY);
            }
        }

        if (closable(card.card)) {
            extractControls(graphics, card, x, y, mouseX, mouseY, now);
        } else if (pin(card.card) != null) {
            extractPin(graphics, card, x, y, mouseX, mouseY);
        }

        if (holdKey != null && holdKey.equals(card.key())) {
            int fill = Math.round(height * holdProgress);
            graphics.fill(x, y + height - fill, x + width, y + height, 0xB8C81E1E);
        }
        if (collapsing) {
            float t = Math.min(1.0F, (now - collapseStart) / (float) COLLAPSE_NANOS);
            graphics.fill(x, y, x + width, y + height, (Math.round(0xC0 * t) << 24) | 0xC81E1E);
        }
        graphics.pose().popMatrix();
    }

    private void extractLive(GuiGraphicsExtractor graphics, Live live, int x, int y, int width, int pictureHeight, int mouseX, int mouseY) {
        LocalPlayer player = live.parked == null ? minecraft.player : live.parked.player();
        ClientLevel level = live.parked == null ? minecraft.level : live.parked.level();
        extractPicture(graphics, live.key, x, y, width, pictureHeight);

        int badgeX = x + 4;
        if (live.parked == null) {
            badgeX = badge(graphics, badgeX, y + 4, CURRENT, GOLD, 0xFF412402, x + width);
        }
        int unread = SessionCards.unread(live.key);
        if (unread > 0) {
            badgeX = badge(graphics, badgeX, y + 4, Component.translatable("multisession.card.unread", unread), 0xC0000000, WHITE, x + width);
        }
        if (player != null && player.isDeadOrDying()) {
            badge(graphics, badgeX, y + 4, DEAD, 0xFF501313, WHITE, x + width);
        } else if (live.parked != null && SessionCards.hurt(live.key)) {
            badge(graphics, badgeX, y + 4, HURT, 0xFFA32D2D, WHITE, x + width);
        }

        if (player != null && level != null && pictureHeight >= 90) {
            BlockPos pos = player.blockPosition();
            Identifier dimension = level.dimension().identifier();
            Component place = Component.translatable("multisession.card.place", pos.getX(), pos.getY(), pos.getZ(),
                    Component.translatableWithFallback("multisession.dimension." + dimension.toLanguageKey(), dimension.getPath()));
            int placeWidth = Math.max(10, width - 8);
            String placeText = ellipsize(place.getString(), placeWidth);
            graphics.fill(x, y + pictureHeight - 13, x + Math.min(width, font.width(placeText) + 8), y + pictureHeight, 0x90000000);
            graphics.text(font, placeText, x + 4, y + pictureHeight - 11, WHITE, false);
        }

        int stripY = y + pictureHeight;
        graphics.fill(x, stripY, x + width, stripY + STRIP, 0xF01F2229);
        ClientPacketListener connection = live.parked == null ? minecraft.getConnection() : player != null ? player.connection : null;
        PlayerSkin skin = player != null ? player.getSkin() : null;
        extractFace(graphics, skin, x + 4, stripY + 4);

        int hudWidth = player != null ? extractHud(graphics, player, connection, x + width - 4, stripY, width, mouseX, mouseY) : 0;
        int textX = x + 24;
        int textWidth = Math.max(10, x + width - 4 - hudWidth - textX - 4);
        Component name = live.parked == null ? SessionCards.activeName(minecraft) : MultisessionConfig.displayName(live.parked.name(), live.parked.address());
        String nickname = live.parked == null
                ? minecraft.getConnection() != null ? minecraft.getConnection().getLocalGameProfile().name() : ""
                : live.parked.nickname();
        Component when;
        if (live.parked == null) {
            when = minecraft.level == null ? CONNECTING : NOW;
        } else if (!live.parked.isInWorld()) {
            when = RECONFIGURING;
        } else if (live.parked.world() != null) {
            when = Component.translatable("multisession.card.paused", duration(SessionCards.awayNanos(live.key)));
        } else {
            when = Component.translatable("multisession.card.ago", duration(SessionCards.awayNanos(live.key)));
        }
        graphics.text(font, ellipsize(name.getString(), textWidth), textX, stripY + 4, WHITE, false);
        graphics.text(font, ellipsize(Component.translatable("multisession.session.label", nickname, when).getString(), textWidth),
                textX, stripY + 14, MUTED, false);
    }

    private int extractHud(GuiGraphicsExtractor graphics, LocalPlayer player, @Nullable ClientPacketListener connection, int right, int stripY, int width,
            int mouseX, int mouseY) {
        float health = player.getMaxHealth() > 0.0F ? Math.min(player.getHealth(), player.getMaxHealth()) * 20.0F / player.getMaxHealth() : 0.0F;
        int halves = Mth.ceil(health);
        int food = player.getFoodData().getFoodLevel();
        int latency = latency(player, connection);
        Identifier ping = PING[latency < 0 ? 0 : latency < 150 ? 5 : latency < 300 ? 4 : latency < 600 ? 3 : latency < 1000 ? 2 : 1];
        if (width >= 230) {
            int rowsLeft = right - 81;
            for (int i = 0; i < 10; i++) {
                int heartX = rowsLeft + i * 8;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, heartX, stripY + 3, 9, 9);
                if (halves >= i * 2 + 2) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_FULL, heartX, stripY + 3, 9, 9);
                } else if (halves == i * 2 + 1) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_HALF, heartX, stripY + 3, 9, 9);
                }
                int foodX = right - 9 - i * 8;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        food >= i * 2 + 2 ? FOOD_FULL : food == i * 2 + 1 ? FOOD_HALF : FOOD_EMPTY, foodX, stripY + 13, 9, 9);
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ping, rowsLeft - 14, stripY + 8, 10, 8);
            pingTooltip(graphics, latency, rowsLeft - 14, stripY + 8, mouseX, mouseY);
            return 81 + 14;
        }
        String hearts = halves % 2 == 0 ? String.valueOf(halves / 2) : String.format(Locale.ROOT, "%.1f", halves / 2.0F);
        String hunger = String.valueOf(food / 2);
        int foodX = right - font.width(hunger) - 11;
        int heartX = foodX - 4 - font.width(hearts) - 11;
        int pingX = heartX - 14;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_FULL, heartX, stripY + 8, 9, 9);
        graphics.text(font, hearts, heartX + 11, stripY + 9, WHITE, false);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FOOD_FULL, foodX, stripY + 8, 9, 9);
        graphics.text(font, hunger, foodX + 11, stripY + 9, WHITE, false);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ping, pingX, stripY + 8, 10, 8);
        pingTooltip(graphics, latency, pingX, stripY + 8, mouseX, mouseY);
        return right - pingX;
    }

    private void pingTooltip(GuiGraphicsExtractor graphics, int latency, int x, int y, int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, x - 1, y - 2, 12, 12) && !dragging) {
            graphics.setTooltipForNextFrame(font, latency < 0 ? PING_UNKNOWN : Component.translatable("multisession.card.ping", latency), mouseX, mouseY);
        }
    }

    private static Component duration(long nanos) {
        long seconds = Math.max(0L, TimeUnit.NANOSECONDS.toSeconds(nanos));
        if (seconds < 60L) {
            return Component.translatable("multisession.time.seconds", seconds);
        }
        if (seconds < 3600L) {
            return Component.translatable("multisession.time.minutes", seconds / 60L, seconds % 60L);
        }
        return Component.translatable("multisession.time.hours", seconds / 3600L, seconds / 60L % 60L);
    }

    private static int latency(LocalPlayer player, @Nullable ClientPacketListener connection) {
        PlayerInfo info = connection != null ? connection.getPlayerInfo(player.getUUID()) : null;
        return info != null ? info.getLatency() : -1;
    }

    private void extractLost(GuiGraphicsExtractor graphics, Lost lost, int x, int y, int width, int pictureHeight, int mouseX, int mouseY) {
        extractPicture(graphics, lost.session.connection(), x, y, width, pictureHeight);
        graphics.fill(x, y, x + width, y + pictureHeight, 0xC8141414);
        int[] button = reconnectButton(new Placed(lost, x, y, width, pictureHeight + STRIP), lost);
        List<FormattedCharSequence> lines = font.split(lost.session.reason(), width - 16);
        int shown = Math.min(lines.size(), Math.max(1, (pictureHeight - (button != null ? BUTTON_HEIGHT + 22 : 16)) / 10));
        int textTop = y + (pictureHeight - shown * 10 - (button != null ? BUTTON_HEIGHT + 4 : 0)) / 2;
        for (int i = 0; i < shown; i++) {
            graphics.centeredText(font, lines.get(i), x + width / 2, textTop + i * 10, 0xFFE0DED6);
        }
        if (button != null) {
            boolean enabled = QuickJoin.canReconnect(minecraft);
            extractButton(graphics, RECONNECT, button[0], button[1], button[2], enabled, inside(mouseX, mouseY, button[0], button[1], button[2], BUTTON_HEIGHT));
        }
        int stripY = y + pictureHeight;
        graphics.fill(x, stripY, x + width, stripY + STRIP, 0xF01F2229);
        extractFace(graphics, lost.session.skin(), x + 4, stripY + 4);
        int textWidth = Math.max(10, width - 28);
        graphics.text(font, ellipsize(MultisessionConfig.displayName(lost.session.name(), lost.session.address()).getString(), textWidth),
                x + 24, stripY + 4, 0xFFB4B2A9, false);
        graphics.text(font, ellipsize(Component.translatable("multisession.session.label", lost.session.nickname(),
                Component.translatable("multisession.card.lost", duration(lost.session.lostNanos()))).getString(), textWidth),
                x + 24, stripY + 14, 0xFFE24B4A, false);
    }

    private int @Nullable [] reconnectButton(Placed card, Lost lost) {
        if (lost.session.address() == null) {
            return null;
        }
        int pictureHeight = card.height - STRIP;
        int width = Math.min(card.width - 16, font.width(RECONNECT) + 20);
        int lines = Math.min(font.split(lost.session.reason(), card.width - 16).size(), Math.max(1, (pictureHeight - BUTTON_HEIGHT - 22) / 10));
        int top = card.y + (pictureHeight - lines * 10 - BUTTON_HEIGHT - 4) / 2 + lines * 10 + 4;
        return new int[] {card.x + (card.width - width) / 2, Math.min(top, card.y + pictureHeight - BUTTON_HEIGHT - 2), width};
    }

    private void extractClosing(GuiGraphicsExtractor graphics, Closing closing, int x, int y, int width, int pictureHeight) {
        extractPicture(graphics, closing.world.key(), x, y, width, pictureHeight);
        graphics.fill(x, y, x + width, y + pictureHeight, 0xB0141414);
        graphics.centeredText(font, SAVING, x + width / 2, y + pictureHeight / 2 - 4, WHITE);
        int stripY = y + pictureHeight;
        graphics.fill(x, stripY, x + width, stripY + STRIP, 0xF01F2229);
        graphics.text(font, ellipsize(closing.world.label().getString(), width - 8), x + 4, stripY + 8, MUTED, false);
    }

    private void extractNew(GuiGraphicsExtractor graphics, Placed card, int x, int y, int width, int height, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + height, 0x70000000);
        graphics.outline(x, y, width, height, 0xFF5F5E5A);
        int[] buttons = newButtons(new Placed(card.card, x, y, width, height));
        int free = buttons[1] - y;
        if (free >= 34) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + width / 2.0F, y + free / 2.0F - 10.0F);
            graphics.pose().scale(2.0F, 2.0F);
            graphics.centeredText(font, "+", 0, -4, 0xFFD3D1C7);
            graphics.pose().popMatrix();
            graphics.centeredText(font, NEW, x + width / 2, y + free / 2 + 4, MUTED);
        } else if (free >= 12) {
            graphics.centeredText(font, NEW, x + width / 2, y + (free - 8) / 2, MUTED);
        }
        boolean enabled = SessionManager.canSwitch(minecraft);
        boolean serverHovered = inside(mouseX, mouseY, buttons[0], buttons[1], buttons[2], BUTTON_HEIGHT);
        boolean worldHovered = inside(mouseX, mouseY, buttons[3], buttons[1], buttons[2], BUTTON_HEIGHT);
        extractButton(graphics, NEW_SERVER, buttons[0], buttons[1], buttons[2], enabled, serverHovered);
        extractButton(graphics, NEW_WORLD, buttons[3], buttons[1], buttons[2], enabled, worldHovered);
        if ((serverHovered || worldHovered) && SessionManager.isPublishedWorld(minecraft)) {
            graphics.setTooltipForNextFrame(font, font.split(PUBLISHED, 200), mouseX, mouseY);
        } else if (worldHovered && SessionManager.hasWorld(minecraft)) {
            graphics.setTooltipForNextFrame(font, font.split(WORLD_REPLACES, 200), mouseX, mouseY);
        }
    }

    private int[] newButtons(Placed card) {
        int buttonWidth = (card.width - 12) / 2;
        int top = card.y + card.height - BUTTON_HEIGHT - 4;
        return new int[] {card.x + 4, top, buttonWidth, card.x + card.width - 4 - buttonWidth};
    }

    private void extractButton(GuiGraphicsExtractor graphics, Component label, int x, int y, int width, boolean enabled, boolean hovered) {
        Identifier sprite = !enabled ? BUTTON_DISABLED : hovered ? BUTTON_HIGHLIGHTED : BUTTON;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, BUTTON_HEIGHT);
        graphics.centeredText(font, ellipsize(label.getString(), width - 6), x + width / 2, y + 6, enabled ? WHITE : 0xFFA0A0A0);
    }

    private void extractControls(GuiGraphicsExtractor graphics, Placed card, int x, int y, int mouseX, int mouseY, long now) {
        int closeX = x + card.width - 4 - CONTROL;
        int offset = 0;
        if (wiggleKey != null && wiggleKey.equals(card.key()) && now - wiggleStart < WIGGLE_NANOS) {
            float t = (now - wiggleStart) / (float) WIGGLE_NANOS;
            offset = Math.round(Mth.sin(t * Mth.PI * 4.0F) * 3.0F * (1.0F - t));
        }
        boolean hovered = inside(mouseX, mouseY, closeX, y + 4, CONTROL, CONTROL);
        graphics.fill(closeX + offset, y + 4, closeX + offset + CONTROL, y + 4 + CONTROL, hovered ? 0xFFA32D2D : 0xA0000000);
        graphics.centeredText(font, "✕", closeX + offset + CONTROL / 2, y + 7, WHITE);
        if (now < hintUntil && wiggleKey != null && wiggleKey.equals(card.key())) {
            graphics.setTooltipForNextFrame(font, HOLD, closeX, y + 4 + CONTROL + 12);
        }
        if (pin(card.card) != null) {
            extractPin(graphics, card, x, y, mouseX, mouseY);
        }
    }

    private void extractPin(GuiGraphicsExtractor graphics, Placed card, int x, int y, int mouseX, int mouseY) {
        Pin pin = pin(card.card);
        if (pin == null) {
            return;
        }
        int pinX = pinX(new Placed(card.card, x, y, card.width, card.height));
        boolean pinned = MultisessionConfig.isPinned(pin.address);
        boolean hovered = inside(mouseX, mouseY, pinX, y + 4, CONTROL, CONTROL);
        graphics.fill(pinX, y + 4, pinX + CONTROL, y + 4 + CONTROL, hovered ? 0xC0404040 : 0xA0000000);
        graphics.centeredText(font, pinned ? "★" : "☆", pinX + CONTROL / 2, y + 7, pinned ? GOLD : WHITE);
        if (hovered) {
            graphics.setTooltipForNextFrame(font, pinned ? UNPIN : PIN, mouseX, mouseY);
        }
    }

    private void extractPicture(GuiGraphicsExtractor graphics, Connection key, int x, int y, int width, int height) {
        SessionSnapshots.Snapshot snapshot = SessionSnapshots.get(key);
        GpuTextureView view = snapshot != null ? snapshot.view() : null;
        if (view == null || height <= 0) {
            graphics.fill(x, y, x + width, y + Math.max(0, height), 0xFF2A2D35);
            return;
        }
        float box = width / (float) height;
        float u0 = 0.0F;
        float u1 = 1.0F;
        float v0 = 0.0F;
        float v1 = 1.0F;
        if (snapshot.aspect() > box) {
            float keep = box / snapshot.aspect();
            u0 = (1.0F - keep) / 2.0F;
            u1 = u0 + keep;
        } else {
            float keep = snapshot.aspect() / box;
            v0 = (1.0F - keep) / 2.0F;
            v1 = v0 + keep;
        }
        graphics.blit(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, x + width, y + height, u0, u1, 1.0F - v0, 1.0F - v1);
    }

    private void extractFace(GuiGraphicsExtractor graphics, @Nullable PlayerSkin skin, int x, int y) {
        if (skin != null) {
            PlayerFaceExtractor.extractRenderState(graphics, skin, x, y, 16);
        } else {
            graphics.fill(x, y, x + 16, y + 16, 0xFF5F5E5A);
        }
    }

    private int badge(GuiGraphicsExtractor graphics, int x, int y, Component text, int background, int color, int limit) {
        int width = font.width(text) + 6;
        if (x + width > limit - 2 * CONTROL - 8) {
            return x;
        }
        graphics.fill(x, y, x + width, y + 12, background);
        graphics.text(font, text, x + 3, y + 2, color, false);
        return x + width + 3;
    }

    private String ellipsize(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    private enum Hit {
        NONE,
        BODY,
        CLOSE,
        PIN,
        RECONNECT,
        NEW_SERVER,
        NEW_WORLD
    }

    private record Pin(String name, String address) {
    }

    private record Placed(Card card, int x, int y, int width, int height) {

        Object key() {
            return switch (card) {
                case Live live -> live.key;
                case Lost lost -> lost.session.connection();
                case Closing closing -> closing.world.key();
                case NewCard ignored -> NewCard.INSTANCE;
            };
        }
    }

    private sealed interface Card permits Live, Lost, Closing, NewCard {
    }

    private record Live(Connection key, @Nullable ClientSession parked) implements Card {
    }

    private record Lost(LostSession session) implements Card {
    }

    private record Closing(SessionCards.ClosingWorld world) implements Card {
    }

    private enum NewCard implements Card {
        INSTANCE
    }
}
