package com.carya.energynews.discovery;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Component
public class NewsDiscoveryQueryFactory {

    private final Clock clock;

    public NewsDiscoveryQueryFactory(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Discovery clock is required");
    }

    public NewsDiscoveryQuery create(
            String keyword,
            LocalDate from,
            LocalDate to,
            int limit
    ) {
        return new NewsDiscoveryQuery(
                keyword,
                startOfDate(from),
                endOfDate(to),
                limit
        );
    }

    private Instant startOfDate(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant endOfDate(LocalDate date) {
        if (date == null) {
            return null;
        }

        Instant now = clock.instant();
        LocalDate currentUtcDate = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (!date.isBefore(currentUtcDate)) {
            return now;
        }
        return date.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
    }
}
