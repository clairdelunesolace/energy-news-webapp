package com.carya.energynews.filter;

import com.carya.energynews.collection.CollectedArticle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordArticleFilterTest {

    @Test
    void acceptsTitleKeywordMatch() {
        KeywordArticleFilter filter = filter("energy storage");

        FilterResult result = filter.evaluate(article(
                "Energy storage deployment accelerates",
                "Market update"
        ));

        assertThat(result).isEqualTo(new FilterResult(true, "Matched keyword: energy storage"));
    }

    @Test
    void acceptsDescriptionKeywordMatch() {
        KeywordArticleFilter filter = filter("battery storage");

        FilterResult result = filter.evaluate(article(
                "New grid project announced",
                "The project includes a large battery storage system."
        ));

        assertThat(result).isEqualTo(new FilterResult(true, "Matched keyword: battery storage"));
    }

    @Test
    void matchesKeywordsCaseInsensitively() {
        KeywordArticleFilter filter = filter("BESS");

        FilterResult result = filter.evaluate(article(
                "Utility-scale bess market expands",
                null
        ));

        assertThat(result).isEqualTo(new FilterResult(true, "Matched keyword: BESS"));
    }

    @Test
    void rejectsArticleWithoutKeywordMatch() {
        KeywordArticleFilter filter = filter("energy storage", "flow battery");

        FilterResult result = filter.evaluate(article(
                "Solar module production increases",
                "Manufacturers announced new production lines."
        ));

        assertThat(result).isEqualTo(new FilterResult(false, "No configured keyword matched"));
    }

    @Test
    void handlesNullDescription() {
        KeywordArticleFilter filter = filter("sodium-ion");

        FilterResult result = filter.evaluate(article(
                "Unrelated market update",
                null
        ));

        assertThat(result).isEqualTo(new FilterResult(false, "No configured keyword matched"));
    }

    @Test
    void evaluatesMultipleConfiguredKeywords() {
        KeywordArticleFilter filter = filter("energy storage", "LFP", "flow battery");

        FilterResult result = filter.evaluate(article(
                "Flow battery facility begins production",
                "New manufacturing capacity is now online."
        ));

        assertThat(result).isEqualTo(new FilterResult(true, "Matched keyword: flow battery"));
    }

    private KeywordArticleFilter filter(String... keywords) {
        return new KeywordArticleFilter(new NewsFilterProperties(List.of(keywords)));
    }

    private CollectedArticle article(String title, String description) {
        return new CollectedArticle(
                title,
                "https://example.com/article",
                description,
                null,
                null,
                1L
        );
    }
}
