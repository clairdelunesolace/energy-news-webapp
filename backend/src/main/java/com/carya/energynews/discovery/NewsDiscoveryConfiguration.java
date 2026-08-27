package com.carya.energynews.discovery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NewsDiscoveryConfiguration {

    @Bean
    @ConditionalOnBean(NewsDiscoveryProvider.class)
    NewsDiscoveryService newsDiscoveryService(NewsDiscoveryProvider provider) {
        return new NewsDiscoveryService(provider);
    }
}
