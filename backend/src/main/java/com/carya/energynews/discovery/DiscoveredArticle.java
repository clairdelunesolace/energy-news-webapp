package com.carya.energynews.discovery;

import java.time.Instant;

public record DiscoveredArticle(
        String title,
        String url,
        String description,
        String sourceName,
        Instant publishedAt,
        String languageCode
) {

    public DiscoveredArticle(
            String title,
            String url,
            String description,
            String sourceName,
            Instant publishedAt
    ) {
        this(title, url, description, sourceName, publishedAt, null);
    }

    public DiscoveredArticle {
        title = requiredText(title, "Discovered article title is required");
        url = requiredText(url, "Discovered article URL is required");
        languageCode = optionalText(languageCode);
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

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }
}
