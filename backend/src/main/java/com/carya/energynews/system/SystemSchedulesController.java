package com.carya.energynews.system;

import com.carya.energynews.dailybrief.DailyBriefSchedulerProperties;
import com.carya.energynews.watchlistdiscovery.WatchlistDiscoverySchedulerProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/schedules")
public class SystemSchedulesController {

    private final WatchlistDiscoverySchedulerProperties newsDiscovery;
    private final DailyBriefSchedulerProperties dailyBrief;

    public SystemSchedulesController(
            WatchlistDiscoverySchedulerProperties newsDiscovery,
            DailyBriefSchedulerProperties dailyBrief
    ) {
        this.newsDiscovery = newsDiscovery;
        this.dailyBrief = dailyBrief;
    }

    @GetMapping
    public SystemSchedulesResponse getSchedules() {
        return new SystemSchedulesResponse(
                ScheduleResponse.from(newsDiscovery.enabled(), newsDiscovery.cron(), newsDiscovery.zone()),
                ScheduleResponse.from(dailyBrief.enabled(), dailyBrief.cron(), dailyBrief.zone())
        );
    }
}
