package com.carya.energynews.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsSyncScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewsSyncScheduler.class);

    private final NewsSyncService newsSyncService;

    public NewsSyncScheduler(NewsSyncService newsSyncService) {
        this.newsSyncService = newsSyncService;
    }

    @Scheduled(cron = "${app.news-sync.cron}", zone = "${app.news-sync.zone}")
    public void runScheduledSync() {
        try {
            NewsSyncResult result = newsSyncService.syncAllEnabledSources();
            LOGGER.info(
                    "Scheduled news synchronization completed: collected={}, filteredOut={}, saved={}, duplicates={}, failedSources={}",
                    result.collected(),
                    result.filteredOut(),
                    result.saved(),
                    result.duplicates(),
                    result.failedSources()
            );
        } catch (Exception exception) {
            LOGGER.error("Scheduled news synchronization failed unexpectedly", exception);
        }
    }
}
