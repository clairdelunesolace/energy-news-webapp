package com.carya.energynews.dailybriefanalysis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DailyBriefAiProperties.class)
public class DailyBriefAiConfiguration {
}
