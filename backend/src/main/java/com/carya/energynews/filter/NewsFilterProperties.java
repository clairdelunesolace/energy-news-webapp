package com.carya.energynews.filter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.news-filter")
public record NewsFilterProperties(List<String> keywords) {

    public NewsFilterProperties {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
