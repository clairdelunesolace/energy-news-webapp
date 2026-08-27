package com.carya.energynews.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewsDiscoveryQueryTest {

    private static final Instant FROM = Instant.parse("2026-08-20T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-27T00:00:00Z");

    @Test
    void acceptsProviderNeutralQuery() {
        NewsDiscoveryQuery query = new NewsDiscoveryQuery(
                "battery energy storage",
                FROM,
                TO,
                20
        );

        assertThat(query.keyword()).isEqualTo("battery energy storage");
        assertThat(query.from()).isEqualTo(FROM);
        assertThat(query.to()).isEqualTo(TO);
        assertThat(query.limit()).isEqualTo(20);
    }

    @Test
    void stripsSurroundingKeywordWhitespace() {
        NewsDiscoveryQuery query = new NewsDiscoveryQuery("  800VDC\n", null, null, 1);

        assertThat(query.keyword()).isEqualTo("800VDC");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsBlankKeyword(String keyword) {
        assertThatThrownBy(() -> new NewsDiscoveryQuery(keyword, FROM, TO, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Discovery keyword must not be blank");
    }

    @Test
    void rejectsNullKeyword() {
        assertThatThrownBy(() -> new NewsDiscoveryQuery(null, FROM, TO, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Discovery keyword is required");
    }

    @Test
    void acceptsOpenEndedAndEqualTimeRanges() {
        assertThat(new NewsDiscoveryQuery("BESS", FROM, null, 20).to()).isNull();
        assertThat(new NewsDiscoveryQuery("BESS", null, TO, 20).from()).isNull();
        assertThat(new NewsDiscoveryQuery("BESS", FROM, FROM, 20).to()).isEqualTo(FROM);
    }

    @Test
    void rejectsReversedTimeRange() {
        assertThatThrownBy(() -> new NewsDiscoveryQuery("BESS", TO, FROM, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Discovery start time must not be after end time");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveLimit(int limit) {
        assertThatThrownBy(() -> new NewsDiscoveryQuery("BESS", FROM, TO, limit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Discovery limit must be between 1 and 100");
    }

    @Test
    void rejectsLimitAboveMaximum() {
        assertThatThrownBy(() -> new NewsDiscoveryQuery("BESS", FROM, TO, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Discovery limit must be between 1 and 100");
    }
}
