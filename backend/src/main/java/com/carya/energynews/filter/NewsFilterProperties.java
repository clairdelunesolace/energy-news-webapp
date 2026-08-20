package com.carya.energynews.filter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.news-filter")
public record NewsFilterProperties(
        int threshold,
        List<String> strongKeywords,
        List<String> weakKeywords
) {

    public NewsFilterProperties {
        threshold = threshold > 0 ? threshold : 3;
        strongKeywords = strongKeywords == null ? List.of() : List.copyOf(strongKeywords);
        weakKeywords = weakKeywords == null ? List.of() : List.copyOf(weakKeywords);
    }
}
