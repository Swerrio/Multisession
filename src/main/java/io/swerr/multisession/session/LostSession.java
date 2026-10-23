package io.swerr.multisession.session;

import io.swerr.multisession.client.MultisessionConfig;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

public final class LostSession {

    final Connection connection;
    final SessionAccount account;
    private final String name;
    private final String nickname;
    private final @Nullable String address;
    private final Component reason;
    private final @Nullable PlayerSkin skin;
    private final long lostAt = System.nanoTime();

    LostSession(ClientSession session, Component reason) {
        this.connection = session.connection;
        this.account = session.context.account;
        this.name = session.name();
        this.nickname = session.nickname();
        this.address = session.address();
        this.reason = reason;
        this.skin = session.context.player != null ? session.context.player.getSkin() : null;
    }

    public Connection connection() {
        return connection;
    }

    public String name() {
        return name;
    }

    public String nickname() {
        return nickname;
    }

    public @Nullable String address() {
        return address;
    }

    public Component reason() {
        return reason;
    }

    public @Nullable PlayerSkin skin() {
        return skin;
    }

    public long lostNanos() {
        return System.nanoTime() - lostAt;
    }

    public Component label() {
        return Component.translatable("multisession.session.label", MultisessionConfig.displayName(name, address), nickname);
    }
}
