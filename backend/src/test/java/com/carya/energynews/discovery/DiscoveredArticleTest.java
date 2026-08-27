package com.carya.energynews.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscoveredArticleTest {

    @Test
    void storesProviderNeutralCandidateAndNormalizesRequiredText() {
        Instant publishedAt = Instant.parse("2026-08-26T10:00:00Z");

        DiscoveredArticle article = new DiscoveredArticle(
                "  Grid battery project announced  ",
                "  https://example.com/grid-battery  ",
                "Project description",
                "Example News",
                publishedAt
        );

        assertThat(article.title()).isEqualTo("Grid battery project announced");
        assertThat(article.url()).isEqualTo("https://example.com/grid-battery");
        assertThat(article.description()).isEqualTo("Project description");
        assertThat(article.sourceName()).isEqualTo("Example News");
        assertThat(article.publishedAt()).isEqualTo(publishedAt);
    }

    @Test
    void acceptsMissingOptionalMetadata() {
        DiscoveredArticle article = new DiscoveredArticle(
                "Candidate",
                "https://example.com/candidate",
                null,
                null,
                null
        );

        assertThat(article.description()).isNull();
        assertThat(article.sourceName()).isNull();
        assertThat(article.publishedAt()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingTitle(String title) {
        assertThatThrownBy(() -> new DiscoveredArticle(
                title,
                "https://example.com/candidate",
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Discovered article title is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingUrl(String url) {
        assertThatThrownBy(() -> new DiscoveredArticle(
                "Candidate",
                url,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Discovered article URL is required");
    }
}
