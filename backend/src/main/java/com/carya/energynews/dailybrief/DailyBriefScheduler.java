package com.carya.energynews.dailybrief;

import com.carya.energynews.dailybriefanalysis.DailyBriefAiProviderUnavailableException;
import com.carya.energynews.dailybriefanalysis.DailyBriefAnalysisService;
import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlist.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "app.daily-brief.scheduler", name = "enabled", havingValue = "true")
public class DailyBriefScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyBriefScheduler.class);

    private final WatchlistRepository watchlistRepository;
    private final DailyBriefService dailyBriefService;
    private final DailyBriefAnalysisService analysisService;
    private final DailyBriefSchedulerProperties properties;
    private final Clock clock;
    private final AtomicBoolean runInProgress = new AtomicBoolean();

    public DailyBriefScheduler(
            WatchlistRepository watchlistRepository,
            DailyBriefService dailyBriefService,
            DailyBriefAnalysisService analysisService,
            DailyBriefSchedulerProperties properties,
            Clock clock
    ) {
        this.watchlistRepository = watchlistRepository;
        this.dailyBriefService = dailyBriefService;
        this.analysisService = analysisService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.daily-brief.scheduler.cron}", zone = "${app.daily-brief.scheduler.zone}")
    public void triggerScheduledGeneration() {
        runScheduledGeneration();
    }

    public ScheduledDailyBriefResult runScheduledGeneration() {
        Instant runAt = clock.instant();
        LocalDate briefDate = LocalDate.ofInstant(runAt, properties.zoneId())
                .plusDays(properties.dayOffset());
        RunTotals totals = new RunTotals();
        if (!runInProgress.compareAndSet(false, true)) {
            LOGGER.warn("Daily brief scheduled run skipped because one is in progress");
            return totals.toResult(runAt, briefDate, true, false);
        }

        try {
            LOGGER.info("Daily brief scheduled run started: date={}, zone={}", briefDate, properties.zone());
            for (Watchlist watchlist : watchlistRepository.findAllByEnabledTrueOrderByIdAsc()) {
                if (!watchlist.isEnabled()) {
                    continue;
                }
                totals.watchlistsProcessed++;
                DailyBriefResponse brief;
                try {
                    // The existing service owns its transaction, ranking, limit and snapshot invalidation.
                    brief = dailyBriefService.generate(new GenerateDailyBriefRequest(
                            watchlist.getId(), briefDate, null
                    ));
                    totals.briefsGenerated++;
                } catch (Exception exception) {
                    totals.watchlistsFailed++;
                    LOGGER.warn("Scheduled daily brief generation failed: watchlistId={}, failureType={}",
                            watchlist.getId(), exception.getClass().getSimpleName());
                    continue;
                }

                if (brief.itemCount() == 0) {
                    totals.emptyBriefs++;
                    totals.aiSkipped++;
                    continue;
                }
                try {
                    analysisService.generate(brief.id());
                    totals.aiGenerated++;
                } catch (DailyBriefAiProviderUnavailableException exception) {
                    totals.aiSkipped++;
                    LOGGER.info("Scheduled daily brief AI skipped: watchlistId={}, reason=providerUnavailable",
                            watchlist.getId());
                } catch (Exception exception) {
                    totals.aiFailed++;
                    LOGGER.warn("Scheduled daily brief AI failed: watchlistId={}, failureType={}",
                            watchlist.getId(), exception.getClass().getSimpleName());
                }
            }
            ScheduledDailyBriefResult result = totals.toResult(runAt, briefDate, false, false);
            LOGGER.info("Daily brief scheduled run completed: watchlistsProcessed={}, watchlistsFailed={}, briefsGenerated={}, emptyBriefs={}, aiGenerated={}, aiSkipped={}, aiFailed={}",
                    result.watchlistsProcessed(), result.watchlistsFailed(), result.briefsGenerated(),
                    result.emptyBriefs(), result.aiGenerated(), result.aiSkipped(), result.aiFailed());
            return result;
        } catch (Exception exception) {
            LOGGER.error("Daily brief scheduled run failed unexpectedly: failureType={}",
                    exception.getClass().getSimpleName());
            return totals.toResult(runAt, briefDate, false, true);
        } finally {
            runInProgress.set(false);
        }
    }

    private static final class RunTotals {
        private int watchlistsProcessed;
        private int watchlistsFailed;
        private int briefsGenerated;
        private int emptyBriefs;
        private int aiGenerated;
        private int aiSkipped;
        private int aiFailed;

        private ScheduledDailyBriefResult toResult(
                Instant runAt, LocalDate briefDate, boolean skippedOverlap, boolean schedulerFailed
        ) {
            return new ScheduledDailyBriefResult(runAt, briefDate, watchlistsProcessed, watchlistsFailed,
                    briefsGenerated, emptyBriefs, aiGenerated, aiSkipped, aiFailed, skippedOverlap, schedulerFailed);
        }
    }
}
