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
        havingValue = "gnews"
)
@EnableConfigurationProperties(GNewsDiscoveryProperties.class)
public class GNewsDiscoveryConfiguration {

    @Bean
    GNewsNewsDiscoveryProvider gNewsNewsDiscoveryProvider(
            GNewsDiscoveryProperties properties,
            ObjectMapper objectMapper
    ) {
        if (properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "GNEWS_API_KEY must be configured and must not be blank when GNews discovery is selected"
            );
        }
        return new GNewsNewsDiscoveryProvider(
                properties.apiKey(),
                properties.maxResultsPerRequest(),
                objectMapper
        );
    }

    @Bean
    NewsDiscoveryService newsDiscoveryService(GNewsNewsDiscoveryProvider provider) {
        return new NewsDiscoveryService(provider);
    }
}
