package com.carya.energynews.system;

import java.util.Locale;
import java.util.regex.Pattern;

public record ScheduleResponse(boolean enabled, String cron, String zone, String dailyTime) {

    private static final Pattern SIMPLE_DAILY_CRON = Pattern.compile(
            "(\\d{1,2})\\s+(\\d{1,2})\\s+(\\d{1,2})\\s+\\*\\s+\\*\\s+\\*"
    );

    static ScheduleResponse from(boolean enabled, String cron, String zone) {
        return new ScheduleResponse(enabled, cron, zone, dailyTime(cron));
    }

    private static String dailyTime(String cron) {
        var match = SIMPLE_DAILY_CRON.matcher(cron.strip());
        if (!match.matches()) {
            return null;
        }
        int second = Integer.parseInt(match.group(1));
        int minute = Integer.parseInt(match.group(2));
        int hour = Integer.parseInt(match.group(3));
        if (second > 59 || minute > 59 || hour > 23) {
            return null;
        }
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }
}
