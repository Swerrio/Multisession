package io.swerr.multisession.session;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.client.resources.server.PackLoadFeedback;
import net.minecraft.client.resources.server.PackReloadConfig;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.jspecify.annotations.Nullable;

public final class PackState {

    public ServerPackManager manager;
    public RepositorySource packSource;
    public PackSource packType;
    public PackLoadFeedback feedback;
    public PackReloadConfig.@Nullable Callbacks pendingReload;
    public List<PackReloadConfig.IdAndPath> requested = List.of();
    public boolean installed;

    void completePendingReload() {
        PackReloadConfig.Callbacks callbacks = pendingReload;
        if (callbacks != null) {
            pendingReload = null;
            callbacks.onSuccess();
        }
    }

    public static List<PackReloadConfig.IdAndPath> withOtherVersions(
            List<PackReloadConfig.IdAndPath> requested, Collection<PackState> others) {
        Set<Path> own = requested.stream().map(PackReloadConfig.IdAndPath::path).collect(Collectors.toSet());
        List<PackReloadConfig.IdAndPath> pooled = new ArrayList<>();
        for (PackReloadConfig.IdAndPath pack : requested) {
            for (PackState other : others) {
                for (PackReloadConfig.IdAndPath version : other.requested) {
                    if (version.id().equals(pack.id()) && !own.contains(version.path()) && !pooled.contains(version)) {
                        pooled.add(version);
                    }
                }
            }
            pooled.add(pack);
        }
        return pooled;
    }

    public static boolean covers(List<PackReloadConfig.IdAndPath> loaded, List<PackReloadConfig.IdAndPath> requested) {
        Set<UUID> ids = requested.stream().map(PackReloadConfig.IdAndPath::id).collect(Collectors.toSet());
        Set<Path> paths = loaded.stream().map(PackReloadConfig.IdAndPath::path).collect(Collectors.toSet());
        return requested.stream().allMatch(pack -> paths.contains(pack.path()))
                && loaded.stream().allMatch(pack -> ids.contains(pack.id()));
    }
}
