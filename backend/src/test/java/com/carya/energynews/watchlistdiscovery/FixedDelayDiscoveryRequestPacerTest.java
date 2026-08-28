package com.carya.energynews.watchlistdiscovery;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedDelayDiscoveryRequestPacerTest {

    @Test
    void firstRequestDoesNotWaitAndLaterRequestsUseConfiguredDelay() {
        List<Long> delays = new ArrayList<>();
        FixedDelayDiscoveryRequestPacer pacer = new FixedDelayDiscoveryRequestPacer(
                10_000,
                delays::add
        );

        pacer.awaitNextRequest();
        assertThat(delays).isEmpty();

        pacer.awaitNextRequest();
        pacer.awaitNextRequest();
        assertThat(delays).containsExactly(10_000L, 10_000L);
    }

    @Test
    void rejectsNegativeDelay() {
        assertThatThrownBy(() -> new FixedDelayDiscoveryRequestPacer(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Discovery request delay must not be negative");
    }
}
