package com.carya.energynews.discovery;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class NewsDiscoveryQueryFactoryTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:15:30Z");

    private final NewsDiscoveryQueryFactory factory = new NewsDiscoveryQueryFactory(
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void mapsPastDatesToUtcDayBounds() {
        NewsDiscoveryQuery query = factory.create(
                "BESS",
                LocalDate.parse("2026-08-25"),
                LocalDate.parse("2026-08-26"),
                10
        );

        assertThat(query.from()).isEqualTo(Instant.parse("2026-08-25T00:00:00Z"));
        assertThat(query.to()).isEqualTo(
                Instant.parse("2026-08-26T23:59:59.999999999Z")
        );
    }

    @Test
    void capsCurrentAndFutureUpperDatesAtCurrentInstant() {
        NewsDiscoveryQuery current = factory.create(
                "BESS",
                null,
                LocalDate.parse("2026-08-27"),
                10
        );
        NewsDiscoveryQuery future = factory.create(
                "BESS",
                null,
                LocalDate.parse("2026-08-30"),
                10
        );

        assertThat(current.to()).isEqualTo(NOW);
        assertThat(future.to()).isEqualTo(NOW);
    }
}
