package com.carya.energynews.discovery;

import java.time.Instant;

public record DiscoveredArticle(
        String title,
        String url,
        String description,
        String sourceName,
        Instant publishedAt
) {

    public DiscoveredArticle {
        title = requiredText(title, "Discovered article title is required");
        url = requiredText(url, "Discovered article URL is required");
    }

    private static String requiredText(String value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
