package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.discovery.DiscoveredArticle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryKeywordMatcherTest {

    private final DiscoveryKeywordMatcher matcher = new DiscoveryKeywordMatcher();

    @Test
    void acceptsNormalizedPhraseInTitleOrDescription() {
        assertThat(matcher.matches(
                "  AI   DATA center ",
                article("Major AI data center buildout", null)
        )).isTrue();
        assertThat(matcher.matches(
                "battery energy storage",
                article("Project announced", "New BATTERY   ENERGY storage capacity")
        )).isTrue();
    }

    @Test
    void rejectsScatteredMultiWordTokens() {
        assertThat(matcher.matches(
                "AI data center",
                article(
                        "AI model improves environmental forecasts",
                        "Researchers used data from a climate center"
                )
        )).isFalse();
    }

    @Test
    void singleWordRequiresSafeTokenBoundaries() {
        assertThat(matcher.matches("NVIDIA", article("NVIDIA launches GB200", null)))
                .isTrue();
        assertThat(matcher.matches("AI", article("Said the company", null)))
                .isFalse();
        assertThat(matcher.matches("800VDC", article("New 800VDC-ready architecture", null)))
                .isTrue();
    }

    private DiscoveredArticle article(String title, String description) {
        return new DiscoveredArticle(
                title,
                "https://example.com/article",
                description,
                "Example",
                null,
                "en"
        );
    }
}
