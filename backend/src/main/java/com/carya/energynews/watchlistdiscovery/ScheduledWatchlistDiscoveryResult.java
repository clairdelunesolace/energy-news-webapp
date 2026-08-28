package com.carya.energynews.watchlistdiscovery;

import java.time.Instant;

public record ScheduledWatchlistDiscoveryResult(
        Instant runAt,
        Instant from,
        Instant to,
        int watchlistsProcessed,
        int watchlistsSkipped,
        int watchlistsFailed,
        int keywordsProcessed,
        int keywordsFailed,
        int keywordsSkippedByRequestLimit,
        int discovered,
        int relevanceRejected,
        int saved,
        int duplicates,
        int keywordMatchesCreated,
        int keywordMatchesExisting,
        int skippedUnsupportedLanguage,
        int skippedInvalidUrl,
        int postProcessingAttempted,
        int metadataTranslationSucceeded,
        int metadataTranslationFailed,
        int contentExtractionSucceeded,
        int contentExtractionFailed,
        int contentTranslationSucceeded,
        int contentTranslationFailed,
        boolean providerUnavailable,
        boolean overlapSkipped,
        boolean schedulerFailed
) {

    static ScheduledWatchlistDiscoveryResult empty(
            Instant runAt,
            Instant from,
            boolean providerUnavailable,
            boolean overlapSkipped,
            boolean schedulerFailed
    ) {
        return new ScheduledWatchlistDiscoveryResult(
                runAt,
                from,
                runAt,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                providerUnavailable,
                overlapSkipped,
                schedulerFailed
        );
    }
}
