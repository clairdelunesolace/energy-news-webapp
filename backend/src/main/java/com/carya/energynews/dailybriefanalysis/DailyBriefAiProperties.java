package com.carya.energynews.dailybriefanalysis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Set;

@ConfigurationProperties(prefix = "app.daily-brief.ai")
public record DailyBriefAiProperties(
        String provider,
        String model,
        @DefaultValue("true") boolean evidenceGuardEnabled
) {

    public static final String DEFAULT_MODEL = "openai/gpt-oss-20b";

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("none", "groq");

    public DailyBriefAiProperties {
        provider = provider == null ? "none" : provider.strip();
        model = model == null ? DEFAULT_MODEL : model.strip();
        if (!SUPPORTED_PROVIDERS.contains(provider)) {
            throw new IllegalArgumentException(
                    "DAILY_BRIEF_AI_PROVIDER must be one of: none, groq"
            );
        }
        if (model.isBlank()) {
            throw new IllegalArgumentException("DAILY_BRIEF_AI_MODEL must not be blank");
        }
    }
}
