package com.carya.energynews.watchlistdiscovery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

import java.time.DateTimeException;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "app.discovery.scheduler")
public record WatchlistDiscoverySchedulerProperties(
        boolean enabled,
        String cron,
        String zone,
        long lookbackHours,
        int limitPerKeyword,
        long delayBetweenKeywordsMs,
        int maxRequestsPerRun
) {

    public WatchlistDiscoverySchedulerProperties {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("Discovery scheduler cron is required");
        }
        CronExpression.parse(cron);
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("Discovery scheduler zone is required");
        }
        try {
            ZoneId.of(zone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(
                    "Discovery scheduler zone must be a valid time-zone ID",
                    exception
            );
        }
        if (lookbackHours < 1 || lookbackHours > 8_760) {
            throw new IllegalArgumentException(
                    "Discovery scheduler lookback hours must be between 1 and 8760"
            );
        }
        if (limitPerKeyword < 1 || limitPerKeyword > 20) {
            throw new IllegalArgumentException(
                    "Discovery scheduler limit per keyword must be between 1 and 20"
            );
        }
        if (delayBetweenKeywordsMs < 0) {
            throw new IllegalArgumentException(
                    "Discovery scheduler delay between keywords must not be negative"
            );
        }
        if (maxRequestsPerRun < 1) {
            throw new IllegalArgumentException(
                    "Discovery scheduler maximum requests per run must be at least 1"
            );
        }
    }
}
