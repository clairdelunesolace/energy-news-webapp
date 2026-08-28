package com.carya.energynews.watchlistdiscovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.security.admin.password=test-password",
        "app.discovery.provider=none",
        "app.discovery.scheduler.enabled=true",
        "app.discovery.scheduler.cron=0 0 0 1 1 *",
        "app.discovery.scheduler.zone=UTC",
        "app.discovery.scheduler.lookback-hours=36",
        "app.discovery.scheduler.limit-per-keyword=5",
        "app.discovery.scheduler.delay-between-keywords-ms=0",
        "app.discovery.scheduler.max-requests-per-run=2",
        "spring.datasource.url=jdbc:h2:mem:discovery-scheduler-startup;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class WatchlistDiscoverySchedulerEnabledStartupTest {

    @Autowired
    private WatchlistDiscoveryScheduler scheduler;

    @Test
    void enabledSchedulerStartsWithoutProviderAndExitsSafely() {
        ScheduledWatchlistDiscoveryResult result = scheduler.runScheduledDiscovery();

        assertThat(result.providerUnavailable()).isTrue();
        assertThat(result.schedulerFailed()).isFalse();
    }
}
