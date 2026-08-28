package com.carya.energynews.watchlistdiscovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatchlistDiscoverySchedulerPropertiesTest {

    @Test
    void validatesLookbackLimitDelayAndRequestCap() {
        assertThatThrownBy(() -> properties(0, 5, 10_000, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lookback hours");
        assertThatThrownBy(() -> properties(36, 21, 10_000, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit per keyword");
        assertThatThrownBy(() -> properties(36, 5, -1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delay between keywords");
        assertThatThrownBy(() -> properties(36, 5, 10_000, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum requests per run");
    }

    @Test
    void validatesCronAndZone() {
        assertThatThrownBy(() -> new WatchlistDiscoverySchedulerProperties(
                true,
                "not a cron",
                "Asia/Shanghai",
                36,
                5,
                10_000,
                20
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WatchlistDiscoverySchedulerProperties(
                true,
                "0 30 20 * * *",
                "Not/AZone",
                36,
                5,
                10_000,
                20
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private WatchlistDiscoverySchedulerProperties properties(
            long lookbackHours,
            int limitPerKeyword,
            long delayBetweenKeywordsMs,
            int maxRequestsPerRun
    ) {
        return new WatchlistDiscoverySchedulerProperties(
                true,
                "0 30 20 * * *",
                "Asia/Shanghai",
                lookbackHours,
                limitPerKeyword,
                delayBetweenKeywordsMs,
                maxRequestsPerRun
        );
    }
}
