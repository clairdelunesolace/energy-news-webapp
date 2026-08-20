package com.carya.energynews.translation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.translation.deepl")
public record DeepLTranslationProperties(
        String baseUrl,
        String apiKey
) {

    private static final String DEFAULT_BASE_URL = "https://api-free.deepl.com";

    public DeepLTranslationProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
        apiKey = apiKey == null ? "" : apiKey;
    }
}
