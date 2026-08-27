package com.carya.energynews.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.discovery.gnews")
public record GNewsDiscoveryProperties(
        String apiKey,
        Integer maxResultsPerRequest
) {

    private static final int DEFAULT_MAX_RESULTS_PER_REQUEST = 10;

    public GNewsDiscoveryProperties {
        apiKey = apiKey == null ? "" : apiKey.strip();
        maxResultsPerRequest = maxResultsPerRequest == null
                ? DEFAULT_MAX_RESULTS_PER_REQUEST
                : maxResultsPerRequest;
        if (maxResultsPerRequest < 1 || maxResultsPerRequest > 100) {
            throw new IllegalArgumentException(
                    "GNEWS_MAX_RESULTS_PER_REQUEST must be between 1 and 100"
            );
        }
    }
}
