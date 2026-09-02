package com.carya.energynews.dailybrief;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

import java.time.DateTimeException;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "app.daily-brief.scheduler")
public record DailyBriefSchedulerProperties(boolean enabled, String cron, String zone, int dayOffset) {

    public DailyBriefSchedulerProperties {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("Daily brief scheduler cron is required");
        }
        CronExpression.parse(cron);
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("Daily brief scheduler zone is required");
        }
        try {
            ZoneId.of(zone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(
                    "Daily brief scheduler zone must be a valid time-zone ID",
                    exception
            );
        }
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
