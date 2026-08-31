package com.carya.energynews.dailybriefanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "app.daily-brief.ai",
        name = "provider",
        havingValue = "groq"
)
@EnableConfigurationProperties(GroqDailyBriefAiProperties.class)
public class GroqDailyBriefAiConfiguration {

    @Bean
    GroqDailyBriefAiProvider groqDailyBriefAiProvider(
            DailyBriefAiProperties aiProperties,
            GroqDailyBriefAiProperties groqProperties,
            ObjectMapper objectMapper
    ) {
        if (groqProperties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "GROQ_API_KEY must be configured and must not be blank when Groq daily brief AI is selected"
            );
        }
        return new GroqDailyBriefAiProvider(
                groqProperties.apiKey(),
                aiProperties.model(),
                objectMapper,
                new DailyBriefAiPromptFactory(objectMapper)
        );
    }
}
