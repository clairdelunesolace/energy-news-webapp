package com.carya.energynews.discovery;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NewsDiscoveryProperties.class)
public class NewsDiscoveryConfiguration {

    @Bean
    Clock discoveryClock() {
        return Clock.systemUTC();
    }
}
