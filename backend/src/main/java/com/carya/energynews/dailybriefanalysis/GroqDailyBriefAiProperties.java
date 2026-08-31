package com.carya.energynews.dailybriefanalysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.daily-brief.ai.groq")
public record GroqDailyBriefAiProperties(String apiKey) {

    public GroqDailyBriefAiProperties {
        apiKey = apiKey == null ? "" : apiKey.strip();
    }
}
