package com.carya.energynews.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "app.discovery",
        name = "provider",
        havingValue = "brave"
)
@EnableConfigurationProperties(BraveNewsDiscoveryProperties.class)
public class BraveNewsDiscoveryConfiguration {

    @Bean
    BraveNewsDiscoveryProvider braveNewsDiscoveryProvider(
            BraveNewsDiscoveryProperties properties,
            ObjectMapper objectMapper
    ) {
        if (properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "BRAVE_SEARCH_API_KEY must be configured and must not be blank when Brave discovery is selected"
            );
        }
        return new BraveNewsDiscoveryProvider(properties.apiKey(), objectMapper);
    }

    @Bean
    NewsDiscoveryService newsDiscoveryService(BraveNewsDiscoveryProvider provider) {
        return new NewsDiscoveryService(provider);
    }
}
