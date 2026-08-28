package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlist.WatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class WatchlistDiscoverySchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-28T04:00:00Z");
    private static final Instant FROM = Instant.parse("2026-08-26T16:00:00Z");

    @Mock
    private WatchlistRepository watchlistRepository;

    @Mock
    private WatchlistDiscoveryService discoveryService;

    private WatchlistDiscoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new WatchlistDiscoveryScheduler(
                watchlistRepository,
                discoveryService,
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void usesConditionalEnableFlagAndConfigurableCronAndZone() throws NoSuchMethodException {
        ConditionalOnProperty conditional = WatchlistDiscoveryScheduler.class
                .getAnnotation(ConditionalOnProperty.class);
        Scheduled scheduled = WatchlistDiscoveryScheduler.class
                .getMethod("triggerScheduledDiscovery")
                .getAnnotation(Scheduled.class);

        assertThat(conditional.prefix()).isEqualTo("app.discovery.scheduler");
        assertThat(conditional.name()).containsExactly("enabled");
        assertThat(conditional.havingValue()).isEqualTo("true");
        assertThat(scheduled.cron()).isEqualTo("${app.discovery.scheduler.cron}");
        assertThat(scheduled.zone()).isEqualTo("${app.discovery.scheduler.zone}");
    }

    @Test
    void processesEnabledWatchlistsSequentiallyAndIsolatesUnexpectedFailure(
            CapturedOutput output
    ) {
        Watchlist disabled = watchlist(1L, "Disabled", false);
        Watchlist failing = watchlist(2L, "Failing", true);
        Watchlist noKeywords = watchlist(3L, "No keywords", true);
        Watchlist successful = watchlist(4L, "Successful", true);
        when(discoveryService.isProviderAvailable()).thenReturn(true);
        when(watchlistRepository.findAllByEnabledTrueOrderByIdAsc())
                .thenReturn(List.of(disabled, failing, noKeywords, successful));
        when(discoveryService.runScheduled(
                eq(2L),
                eq(FROM),
                eq(NOW),
                eq(5),
                any(DiscoveryRequestPacer.class),
                any(DiscoveryRequestBudget.class)
        )).thenThrow(new IllegalStateException("database unavailable"));
        when(discoveryService.runScheduled(
                eq(3L),
                eq(FROM),
                eq(NOW),
                eq(5),
                any(DiscoveryRequestPacer.class),
                any(DiscoveryRequestBudget.class)
        )).thenReturn(new WatchlistDiscoveryExecutionResult(
                response(3L, "No keywords", 0, 0, 0, 0, List.of()),
                0
        ));
        WatchlistDiscoveryKeywordFailure keywordFailure =
                new WatchlistDiscoveryKeywordFailure(41L, "battery", "gnews was rate limited");
        when(discoveryService.runScheduled(
                eq(4L),
                eq(FROM),
                eq(NOW),
                eq(5),
                any(DiscoveryRequestPacer.class),
                any(DiscoveryRequestBudget.class)
        )).thenReturn(new WatchlistDiscoveryExecutionResult(
                response(4L, "Successful", 1, 1, 1, 1, List.of(keywordFailure)),
                2
        ));

        ScheduledWatchlistDiscoveryResult result = scheduler.runScheduledDiscovery();

        assertThat(result.from()).isEqualTo(FROM);
        assertThat(result.to()).isEqualTo(NOW);
        assertThat(result.watchlistsProcessed()).isEqualTo(1);
        assertThat(result.watchlistsSkipped()).isEqualTo(2);
        assertThat(result.watchlistsFailed()).isEqualTo(1);
        assertThat(result.keywordsProcessed()).isEqualTo(1);
        assertThat(result.keywordsFailed()).isEqualTo(1);
        assertThat(result.keywordsSkippedByRequestLimit()).isEqualTo(2);
        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.duplicates()).isEqualTo(1);
        assertThat(result.postProcessingAttempted()).isEqualTo(1);
        assertThat(result.schedulerFailed()).isFalse();
        assertThat(output).contains(
                "watchlistId=4, watchlistName=Successful, keywordId=41, keyword=battery, reason=gnews was rate limited",
                "watchlistsProcessed=1, watchlistsSkipped=2, watchlistsFailed=1"
        );

        verify(discoveryService, never()).runScheduled(
                eq(1L),
                any(),
                any(),
                eq(5),
                any(),
                any()
        );
        InOrder order = inOrder(discoveryService);
        order.verify(discoveryService).runScheduled(
                eq(2L), eq(FROM), eq(NOW), eq(5), any(), any()
        );
        order.verify(discoveryService).runScheduled(
                eq(3L), eq(FROM), eq(NOW), eq(5), any(), any()
        );
        order.verify(discoveryService).runScheduled(
                eq(4L), eq(FROM), eq(NOW), eq(5), any(), any()
        );
    }

    @Test
    void providerUnavailableSkipsSafelyWithoutLoadingWatchlists() {
        when(discoveryService.isProviderAvailable()).thenReturn(false);

        ScheduledWatchlistDiscoveryResult result = scheduler.runScheduledDiscovery();

        assertThat(result.providerUnavailable()).isTrue();
        assertThat(result.schedulerFailed()).isFalse();
        verifyNoInteractions(watchlistRepository);
    }

    @Test
    void preservesPartialCountersFromUnexpectedWatchlistFailure() {
        Watchlist watchlist = watchlist(5L, "Partial", true);
        WatchlistDiscoveryRunResponse partialResponse = response(
                5L,
                "Partial",
                0,
                1,
                2,
                0,
                List.of(new WatchlistDiscoveryKeywordFailure(
                        51L,
                        "battery",
                        "Unexpected processing failure"
                ))
        );
        when(discoveryService.isProviderAvailable()).thenReturn(true);
        when(watchlistRepository.findAllByEnabledTrueOrderByIdAsc())
                .thenReturn(List.of(watchlist));
        when(discoveryService.runScheduled(
                eq(5L),
                eq(FROM),
                eq(NOW),
                eq(5),
                any(DiscoveryRequestPacer.class),
                any(DiscoveryRequestBudget.class)
        )).thenThrow(new WatchlistDiscoveryExecutionException(
                new IllegalStateException("unexpected"),
                new WatchlistDiscoveryExecutionResult(partialResponse, 0)
        ));

        ScheduledWatchlistDiscoveryResult result = scheduler.runScheduledDiscovery();

        assertThat(result.watchlistsFailed()).isEqualTo(1);
        assertThat(result.keywordsFailed()).isEqualTo(1);
        assertThat(result.saved()).isEqualTo(2);
        assertThat(result.postProcessingAttempted()).isEqualTo(2);
        assertThat(result.schedulerFailed()).isFalse();
    }

    @Test
    void overlappingInvocationIsSkipped() {
        AtomicBoolean runInProgress = (AtomicBoolean) ReflectionTestUtils.getField(
                scheduler,
                "runInProgress"
        );
        assertThat(runInProgress).isNotNull();
        runInProgress.set(true);

        ScheduledWatchlistDiscoveryResult result = scheduler.runScheduledDiscovery();

        assertThat(result.overlapSkipped()).isTrue();
        verifyNoInteractions(watchlistRepository, discoveryService);
    }

    private WatchlistDiscoverySchedulerProperties properties() {
        return new WatchlistDiscoverySchedulerProperties(
                true,
                "0 30 20 * * *",
                "Asia/Shanghai",
                36,
                5,
                0,
                20
        );
    }

    private Watchlist watchlist(Long id, String name, boolean enabled) {
        Watchlist watchlist = new Watchlist(name);
        watchlist.setEnabled(enabled);
        ReflectionTestUtils.setField(watchlist, "id", id);
        return watchlist;
    }

    private WatchlistDiscoveryRunResponse response(
            Long watchlistId,
            String watchlistName,
            int keywordsProcessed,
            int keywordsFailed,
            int saved,
            int duplicates,
            List<WatchlistDiscoveryKeywordFailure> failures
    ) {
        return new WatchlistDiscoveryRunResponse(
                watchlistId,
                watchlistName,
                keywordsProcessed,
                keywordsFailed,
                3,
                0,
                saved,
                duplicates,
                1,
                0,
                0,
                0,
                saved,
                saved,
                0,
                saved,
                0,
                saved,
                0,
                failures,
                List.of()
        );
    }
}
