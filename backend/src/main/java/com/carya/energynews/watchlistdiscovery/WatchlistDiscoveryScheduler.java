package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlist.WatchlistNotFoundException;
import com.carya.energynews.watchlist.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(
        prefix = "app.discovery.scheduler",
        name = "enabled",
        havingValue = "true"
)
public class WatchlistDiscoveryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            WatchlistDiscoveryScheduler.class
    );

    private final WatchlistRepository watchlistRepository;
    private final WatchlistDiscoveryService discoveryService;
    private final WatchlistDiscoverySchedulerProperties properties;
    private final Clock clock;
    private final AtomicBoolean runInProgress = new AtomicBoolean();

    public WatchlistDiscoveryScheduler(
            WatchlistRepository watchlistRepository,
            WatchlistDiscoveryService discoveryService,
            WatchlistDiscoverySchedulerProperties properties,
            Clock clock
    ) {
        this.watchlistRepository = watchlistRepository;
        this.discoveryService = discoveryService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${app.discovery.scheduler.cron}",
            zone = "${app.discovery.scheduler.zone}"
    )
    public void triggerScheduledDiscovery() {
        runScheduledDiscovery();
    }

    public ScheduledWatchlistDiscoveryResult runScheduledDiscovery() {
        Instant runAt = clock.instant();
        Instant from = runAt.minus(Duration.ofHours(properties.lookbackHours()));
        if (!runInProgress.compareAndSet(false, true)) {
            LOGGER.warn("Watchlist discovery scheduled run skipped because one is in progress");
            return ScheduledWatchlistDiscoveryResult.empty(
                    runAt,
                    from,
                    false,
                    true,
                    false
            );
        }

        try {
            LOGGER.info(
                    "Watchlist discovery scheduled run started: from={}, to={}",
                    from,
                    runAt
            );
            if (!discoveryService.isProviderAvailable()) {
                LOGGER.warn(
                        "Watchlist discovery scheduled run skipped: discovery provider is not configured"
                );
                return ScheduledWatchlistDiscoveryResult.empty(
                        runAt,
                        from,
                        true,
                        false,
                        false
                );
            }

            RunTotals totals = executeWatchlists(runAt, from);
            ScheduledWatchlistDiscoveryResult result = totals.toResult(runAt, from);
            logCompletion(result);
            return result;
        } catch (Exception exception) {
            LOGGER.error(
                    "Watchlist discovery scheduled run failed unexpectedly: failureType={}",
                    exception.getClass().getSimpleName()
            );
            return ScheduledWatchlistDiscoveryResult.empty(
                    runAt,
                    from,
                    false,
                    false,
                    true
            );
        } finally {
            runInProgress.set(false);
        }
    }

    private RunTotals executeWatchlists(Instant runAt, Instant from) {
        DiscoveryRequestPacer pacer = new FixedDelayDiscoveryRequestPacer(
                properties.delayBetweenKeywordsMs()
        );
        DiscoveryRequestBudget requestBudget = new DiscoveryRequestBudget(
                properties.maxRequestsPerRun()
        );
        RunTotals totals = new RunTotals();
        List<Watchlist> watchlists = watchlistRepository.findAllByEnabledTrueOrderByIdAsc();

        for (Watchlist watchlist : watchlists) {
            if (!watchlist.isEnabled()) {
                totals.watchlistsSkipped++;
                continue;
            }

            try {
                WatchlistDiscoveryExecutionResult execution = discoveryService.runScheduled(
                        watchlist.getId(),
                        from,
                        runAt,
                        properties.limitPerKeyword(),
                        pacer,
                        requestBudget
                );
                totals.add(execution);
                if (execution.response().keywordsProcessed()
                        + execution.response().keywordsFailed() == 0) {
                    totals.watchlistsSkipped++;
                } else {
                    totals.watchlistsProcessed++;
                }
                logKeywordFailures(watchlist, execution.response());
            } catch (WatchlistDiscoveryExecutionException exception) {
                totals.add(exception.partialResult());
                totals.watchlistsFailed++;
                logKeywordFailures(watchlist, exception.partialResult().response());
                LOGGER.warn(
                        "Scheduled watchlist discovery failed: watchlistId={}, watchlistName={}, failureType={}",
                        watchlist.getId(),
                        watchlist.getName(),
                        exception.getCause().getClass().getSimpleName()
                );
            } catch (WatchlistDisabledException | WatchlistNotFoundException exception) {
                totals.watchlistsSkipped++;
                LOGGER.info(
                        "Scheduled watchlist discovery skipped: watchlistId={}, reason={}",
                        watchlist.getId(),
                        exception.getClass().getSimpleName()
                );
            } catch (Exception exception) {
                totals.watchlistsFailed++;
                LOGGER.warn(
                        "Scheduled watchlist discovery failed: watchlistId={}, watchlistName={}, failureType={}",
                        watchlist.getId(),
                        watchlist.getName(),
                        exception.getClass().getSimpleName()
                );
            }
        }
        return totals;
    }

    private void logKeywordFailures(
            Watchlist watchlist,
            WatchlistDiscoveryRunResponse response
    ) {
        for (WatchlistDiscoveryKeywordFailure failure : response.failedKeywords()) {
            LOGGER.warn(
                    "Scheduled watchlist keyword discovery failed: watchlistId={}, watchlistName={}, keywordId={}, keyword={}, reason={}",
                    watchlist.getId(),
                    watchlist.getName(),
                    failure.keywordId(),
                    failure.keyword(),
                    failure.message()
            );
        }
    }

    private void logCompletion(ScheduledWatchlistDiscoveryResult result) {
        LOGGER.info(
                "Watchlist discovery scheduled run completed: watchlistsProcessed={}, watchlistsSkipped={}, watchlistsFailed={}, keywordsProcessed={}, keywordFailures={}, keywordsSkippedByRequestLimit={}, saved={}, duplicates={}, postProcessed={}",
                result.watchlistsProcessed(),
                result.watchlistsSkipped(),
                result.watchlistsFailed(),
                result.keywordsProcessed(),
                result.keywordsFailed(),
                result.keywordsSkippedByRequestLimit(),
                result.saved(),
                result.duplicates(),
                result.postProcessingAttempted()
        );
    }

    private static final class RunTotals {

        private int watchlistsProcessed;
        private int watchlistsSkipped;
        private int watchlistsFailed;
        private int keywordsProcessed;
        private int keywordsFailed;
        private int keywordsSkippedByRequestLimit;
        private int discovered;
        private int relevanceRejected;
        private int saved;
        private int duplicates;
        private int keywordMatchesCreated;
        private int keywordMatchesExisting;
        private int skippedUnsupportedLanguage;
        private int skippedInvalidUrl;
        private int postProcessingAttempted;
        private int metadataTranslationSucceeded;
        private int metadataTranslationFailed;
        private int contentExtractionSucceeded;
        private int contentExtractionFailed;
        private int contentTranslationSucceeded;
        private int contentTranslationFailed;

        private void add(WatchlistDiscoveryExecutionResult execution) {
            WatchlistDiscoveryRunResponse response = execution.response();
            keywordsProcessed += response.keywordsProcessed();
            keywordsFailed += response.keywordsFailed();
            keywordsSkippedByRequestLimit += execution.keywordsSkippedByRequestLimit();
            discovered += response.discovered();
            relevanceRejected += response.relevanceRejected();
            saved += response.saved();
            duplicates += response.duplicates();
            keywordMatchesCreated += response.keywordMatchesCreated();
            keywordMatchesExisting += response.keywordMatchesExisting();
            skippedUnsupportedLanguage += response.skippedUnsupportedLanguage();
            skippedInvalidUrl += response.skippedInvalidUrl();
            postProcessingAttempted += response.postProcessingAttempted();
            metadataTranslationSucceeded += response.metadataTranslationSucceeded();
            metadataTranslationFailed += response.metadataTranslationFailed();
            contentExtractionSucceeded += response.contentExtractionSucceeded();
            contentExtractionFailed += response.contentExtractionFailed();
            contentTranslationSucceeded += response.contentTranslationSucceeded();
            contentTranslationFailed += response.contentTranslationFailed();
        }

        private ScheduledWatchlistDiscoveryResult toResult(Instant runAt, Instant from) {
            return new ScheduledWatchlistDiscoveryResult(
                    runAt,
                    from,
                    runAt,
                    watchlistsProcessed,
                    watchlistsSkipped,
                    watchlistsFailed,
                    keywordsProcessed,
                    keywordsFailed,
                    keywordsSkippedByRequestLimit,
                    discovered,
                    relevanceRejected,
                    saved,
                    duplicates,
                    keywordMatchesCreated,
                    keywordMatchesExisting,
                    skippedUnsupportedLanguage,
                    skippedInvalidUrl,
                    postProcessingAttempted,
                    metadataTranslationSucceeded,
                    metadataTranslationFailed,
                    contentExtractionSucceeded,
                    contentExtractionFailed,
                    contentTranslationSucceeded,
                    contentTranslationFailed,
                    false,
                    false,
                    false
            );
        }
    }
}
