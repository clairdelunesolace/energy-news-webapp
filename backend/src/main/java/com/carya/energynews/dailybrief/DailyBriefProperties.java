package com.carya.energynews.dailybrief;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DateTimeException;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "app.daily-brief")
public record DailyBriefProperties(
        String zone,
        int maxItems
) {

    public DailyBriefProperties {
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("Daily brief zone is required");
        }
        zone = zone.trim();
        try {
            ZoneId.of(zone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(
                    "Daily brief zone must be a valid time-zone ID",
                    exception
            );
        }
        if (maxItems < 1 || maxItems > 20) {
            throw new IllegalArgumentException("Daily brief max items must be between 1 and 20");
        }
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
