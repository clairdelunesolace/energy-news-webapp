package com.carya.energynews.watchlist;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WatchlistKeywordMatcherTest {

    private final WatchlistKeywordMatcher matcher = new WatchlistKeywordMatcher();

    @Test
    void matchesOneKeywordInOriginalTitle() {
        assertThat(matcher.matchTags("Storage microgrid goes online", null,
                List.of("battery storage", "microgrid")))
                .containsExactly("microgrid");
    }

    @Test
    void ignoresCaseAndTrimsForMatchingButPreservesTheConfiguredLabel() {
        assertThat(matcher.matchTags("A MICROGRID opens", null, List.of(" microGrid ")))
                .containsExactly(" microGrid ");
    }

    @Test
    void matchesTitleAndDescriptionInDeterministicKeywordOrder() {
        assertThat(matcher.matchTags("Microgrid opens", "Battery storage uses BESS technology",
                List.of("microgrid", "BESS", "battery storage")))
                .containsExactly("battery storage", "BESS", "microgrid");
    }

    @Test
    void ignoresBlankAndNullKeywords() {
        assertThat(matcher.matchTags("Microgrid", "Summary",
                Arrays.asList("", "  ", "\t\n", null, "microgrid")))
                .containsExactly("microgrid");
    }

    @Test
    void deduplicatesCaseAndWhitespaceEquivalentsRegardlessOfInputOrder() {
        List<String> keywords = List.of("bess", "BESS", " bess ", "BESS");

        List<String> tags = matcher.matchTags("BESS project", "BESS update", keywords);

        assertThat(tags).containsExactly(" bess ");
        assertThat(matcher.matchTags("BESS project", "BESS update", keywords.reversed()))
                .isEqualTo(tags);
    }

    @Test
    void returnsEmptyTagsWhenNothingMatchesOrTextIsMissing() {
        assertThat(matcher.matchTags("Wind project opens", null, List.of("battery storage")))
                .isEmpty();
        assertThat(matcher.matchTags(null, null, List.of("battery storage"))).isEmpty();
        assertThat(matcher.matchTags("Battery storage", "Summary", List.of())).isEmpty();
    }

    @Test
    void treatsKeywordsAsLiteralSubstringsIncludingNonEnglishText() {
        assertThat(matcher.matchTags("Microgrids use C++ for 储能 control", null,
                List.of("microgrid", "C++", "储能")))
                .containsExactly("C++", "microgrid", "储能");
    }

    @Test
    void doesNotJoinTitleAndDescriptionIntoAnArtificialPhraseMatch() {
        assertThat(matcher.matchTags("Battery", "storage expands", List.of("battery storage")))
                .isEmpty();
    }
}
