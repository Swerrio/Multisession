package io.swerr.multisession.session;

import net.minecraft.network.PacketListener;

public final class DeferredPacket implements Runnable {

    private final PacketListener listener;
    private final Runnable task;

    public DeferredPacket(PacketListener listener, Runnable task) {
        this.listener = listener;
        this.task = task;
    }

    @Override
    public void run() {
        ClientSession session = SessionManager.parkedFor(listener);
        if (session != null && session != SessionManager.background()) {
            session.defer(this);
        } else {
            task.run();
        }
    }

    void handle() {
        task.run();
    }
}
