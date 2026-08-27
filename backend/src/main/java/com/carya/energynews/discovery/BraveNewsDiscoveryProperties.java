package com.carya.energynews.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.discovery.brave")
public record BraveNewsDiscoveryProperties(
        String apiKey
) {

    public BraveNewsDiscoveryProperties {
        apiKey = apiKey == null ? "" : apiKey.strip();
    }
}
