package com.carya.energynews.filter;

import com.carya.energynews.collection.CollectedArticle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class KeywordArticleFilter implements ArticleFilter {

    private final List<String> keywords;

    public KeywordArticleFilter(NewsFilterProperties properties) {
        this.keywords = properties.keywords().stream()
                .map(String::trim)
                .filter(keyword -> !keyword.isEmpty())
                .toList();
    }

    @Override
    public FilterResult evaluate(CollectedArticle article) {
        String title = normalize(article.title());
        String description = normalize(article.description());

        for (String keyword : keywords) {
            String normalizedKeyword = normalize(keyword);
            if (title.contains(normalizedKeyword) || description.contains(normalizedKeyword)) {
                return new FilterResult(true, "Matched keyword: " + keyword);
            }
        }

        return new FilterResult(false, "No configured keyword matched");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
