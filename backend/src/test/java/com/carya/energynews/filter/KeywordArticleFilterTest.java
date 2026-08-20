package com.carya.energynews.filter;

import com.carya.energynews.collection.CollectedArticle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordArticleFilterTest {

    @Test
    void acceptsStrongKeywordInTitle() {
        KeywordArticleFilter filter = filter(3, List.of("grid battery"), List.of());

        FilterResult result = filter.evaluate(article(
                "New grid battery project announced",
                "Project details"
        ));

        assertThat(result).isEqualTo(new FilterResult(
                true,
                "score=4, threshold=3, titleStrong=[grid battery]"
        ));
    }

    @Test
    void rejectsStrongKeywordOnlyInDescription() {
        KeywordArticleFilter filter = filter(
                3,
                List.of("battery storage"),
                List.of("battery")
        );

        FilterResult result = filter.evaluate(article(
                "Solar project reaches completion",
                "The project also includes a battery storage system."
        ));

        assertThat(result).isEqualTo(new FilterResult(
                false,
                "score=2, threshold=3, descriptionStrong=[battery storage]"
        ));
    }

    @Test
    void rejectsWeakKeywordOnlyInTitle() {
        KeywordArticleFilter filter = filter(3, List.of(), List.of("battery"));

        FilterResult result = filter.evaluate(article(
                "Battery manufacturing costs decline",
                "Quarterly manufacturing report"
        ));

        assertThat(result).isEqualTo(new FilterResult(
                false,
                "score=2, threshold=3, titleWeak=[battery]"
        ));
    }

    @Test
    void acceptsMultipleWeakMatchesThatReachThreshold() {
        KeywordArticleFilter filter = filter(3, List.of(), List.of("battery", "LFP"));

        FilterResult result = filter.evaluate(article(
                "Battery supplier expands LFP production",
                "Manufacturing update"
        ));

        assertThat(result).isEqualTo(new FilterResult(
                true,
                "score=4, threshold=3, titleWeak=[battery, LFP]"
        ));
    }

    @Test
    void combinesTitleAndDescriptionScores() {
        KeywordArticleFilter filter = filter(
                3,
                List.of(),
                List.of("battery", "power conversion")
        );

        FilterResult result = filter.evaluate(article(
                "Battery supplier announces new product",
                "The system includes power conversion equipment."
        ));

        assertThat(result).isEqualTo(new FilterResult(
                true,
                "score=3, threshold=3, titleWeak=[battery], descriptionWeak=[power conversion]"
        ));
    }

    @Test
    void matchesKeywordsCaseInsensitively() {
        KeywordArticleFilter filter = filter(3, List.of("BESS"), List.of());

        FilterResult result = filter.evaluate(article(
                "Utility-scale bess market expands",
                null
        ));

        assertThat(result).isEqualTo(new FilterResult(
                true,
                "score=4, threshold=3, titleStrong=[BESS]"
        ));
    }

    @Test
    void handlesNullDescription() {
        KeywordArticleFilter filter = filter(3, List.of(), List.of("battery"));

        FilterResult result = filter.evaluate(article(
                "Battery market update",
                null
        ));

        assertThat(result).isEqualTo(new FilterResult(
                false,
                "score=2, threshold=3, titleWeak=[battery]"
        ));
    }

    @Test
    void rejectsArticleWithoutMatches() {
        KeywordArticleFilter filter = filter(
                3,
                List.of("energy storage"),
                List.of("battery")
        );

        FilterResult result = filter.evaluate(article(
                "Solar module production increases",
                "Manufacturers announced new production lines."
        ));

        assertThat(result).isEqualTo(new FilterResult(
                false,
                "score=0, threshold=3, matches=[]"
        ));
    }

    @Test
    void usesConfiguredThreshold() {
        KeywordArticleFilter filter = filter(5, List.of("energy storage"), List.of());

        FilterResult result = filter.evaluate(article(
                "Energy storage deployment accelerates",
                null
        ));

        assertThat(result).isEqualTo(new FilterResult(
                false,
                "score=4, threshold=5, titleStrong=[energy storage]"
        ));
    }

    @Test
    void countsRepeatedKeywordOnlyOncePerField() {
        KeywordArticleFilter filter = filter(3, List.of(), List.of("battery"));

        FilterResult result = filter.evaluate(article(
                "Battery supplier expands battery production for battery cells",
                null
        ));

        assertThat(result).isEqualTo(new FilterResult(
                false,
                "score=2, threshold=3, titleWeak=[battery]"
        ));
    }

    @Test
    void usesDefaultThresholdWhenConfiguredValueIsNotPositive() {
        KeywordArticleFilter filter = filter(0, List.of("battery storage"), List.of());

        FilterResult result = filter.evaluate(article(
                "Solar project reaches completion",
                "The project also includes battery storage."
        ));

        assertThat(result.reason()).contains("score=2", "threshold=3", "battery storage");
        assertThat(result.accepted()).isFalse();
    }

    private KeywordArticleFilter filter(
            int threshold,
            List<String> strongKeywords,
            List<String> weakKeywords
    ) {
        return new KeywordArticleFilter(new NewsFilterProperties(
                threshold,
                strongKeywords,
                weakKeywords
        ));
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
