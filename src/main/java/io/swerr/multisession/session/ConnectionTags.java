package io.swerr.multisession.session;

import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;

public interface ConnectionTags {

    Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> multisession$tags();

    Map<ResourceKey<? extends Registry<?>>, Long> multisession$fingerprints();
}
