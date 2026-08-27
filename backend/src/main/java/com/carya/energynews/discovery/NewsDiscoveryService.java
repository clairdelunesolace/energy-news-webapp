package com.carya.energynews.discovery;

import java.util.List;
import java.util.Objects;

public class NewsDiscoveryService {

    private final NewsDiscoveryProvider provider;

    public NewsDiscoveryService(NewsDiscoveryProvider provider) {
        this.provider = Objects.requireNonNull(provider, "News discovery provider is required");
    }

    public String providerName() {
        return provider.providerName();
    }

    public List<DiscoveredArticle> discover(NewsDiscoveryQuery query) {
        return provider.discover(Objects.requireNonNull(query, "Discovery query is required"));
    }
}
