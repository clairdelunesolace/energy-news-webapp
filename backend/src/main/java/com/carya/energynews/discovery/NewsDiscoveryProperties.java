package com.carya.energynews.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(prefix = "app.discovery")
public record NewsDiscoveryProperties(String provider) {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("none", "brave", "gnews");

    public NewsDiscoveryProperties {
        provider = provider == null ? "none" : provider;
        if (!SUPPORTED_PROVIDERS.contains(provider)) {
            throw new IllegalArgumentException(
                    "NEWS_DISCOVERY_PROVIDER must be one of: none, brave, gnews"
            );
        }
    }
}
