package com.carya.energynews.dailybrief;

import com.carya.energynews.dailybriefanalysis.DailyBriefAiProviderUnavailableException;
import com.carya.energynews.dailybriefanalysis.DailyBriefAnalysisService;
import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlist.WatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class DailyBriefSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-31T17:00:00Z");
    private static final LocalDate DATE = LocalDate.parse("2026-08-31");

    @Mock
    private WatchlistRepository watchlistRepository;
    @Mock
    private DailyBriefService dailyBriefService;
    @Mock
    private DailyBriefAnalysisService analysisService;

    private DailyBriefScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = scheduler("Asia/Shanghai");
    }

    @Test
    void delegatesInIdOrderSkipsDisabledWatchlistsAndKeepsExistingMaxItemsDefault() {
        Watchlist disabled = watchlist(2);
        disabled.setEnabled(false);
        when(watchlistRepository.findAllByEnabledTrueOrderByIdAsc())
                .thenReturn(List.of(watchlist(1), disabled, watchlist(3)));
        when(dailyBriefService.generate(request(1, DATE))).thenReturn(brief(11, 2));
        when(dailyBriefService.generate(request(3, DATE))).thenReturn(brief(33, 1));

        ScheduledDailyBriefResult result = scheduler.runScheduledGeneration();

        InOrder order = inOrder(dailyBriefService, analysisService);
        order.verify(dailyBriefService).generate(new GenerateDailyBriefRequest(1L, DATE, null));
        order.verify(analysisService).generate(11L);
        order.verify(dailyBriefService).generate(new GenerateDailyBriefRequest(3L, DATE, null));
        order.verify(analysisService).generate(33L);
        verifyNoMoreInteractions(dailyBriefService, analysisService);
        assertThat(result).isEqualTo(new ScheduledDailyBriefResult(NOW, DATE,
                2, 0, 2, 0, 2, 0, 0, false, false));
    }

    @ParameterizedTest
    @CsvSource({"Asia/Shanghai,2026-08-31", "UTC,2026-08-30", "America/Los_Angeles,2026-08-30"})
    void usesConfiguredZoneInsteadOfClockOrServerZone(String zone, LocalDate expectedDate) {
        when(watchlistRepository.findAllByEnabledTrueOrderByIdAsc()).thenReturn(List.of(watchlist(1)));
        when(dailyBriefService.generate(request(1, expectedDate))).thenReturn(brief(11, 0));

        ScheduledDailyBriefResult result = scheduler(zone).runScheduledGeneration();

        verify(dailyBriefService).generate(request(1, expectedDate));
        assertThat(result.briefDate()).isEqualTo(expectedDate);
        assertThat(result.emptyBriefs()).isEqualTo(1);
        assertThat(result.aiSkipped()).isEqualTo(1);
        verifyNoInteractions(analysisService);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-08-31T15:59:59Z,2026-08-30",
            "2026-08-31T16:00:00Z,2026-08-31"
    })
    void calculatesPreviousDayAcrossShanghaiCalendarBoundary(Instant runAt, LocalDate expectedDate) {
        when(watchlistRepository.findAllByEnabledTrueOrderByIdAsc()).thenReturn(List.of());

        ScheduledDailyBriefResult result = scheduler("Asia/Shanghai", runAt, -1).runScheduledGeneration();

        assertThat(result.runAt()).isEqualTo(runAt);
        assertThat(result.briefDate()).isEqualTo(expectedDate);
    }

    @Test
    void zeroOffsetCanTargetSchedulerCalendarDate() {
        when(watchlistRepository.findAllByEnabledTrueOrderByIdAsc()).thenReturn(List.of());

        ScheduledDailyBriefResult result = scheduler("Asia/Shanghai", NOW, 0).runScheduledGeneration();

        assertThat(result.briefDate()).isEqualTo(LocalDate.parse("2026-09-01"));
    }

    @Test
    void isolatesDeterministicAndAiFailuresAndReportsEveryOutcomeWithoutSensitiveMessages(CapturedOutput output) {
        when(watchlistRepository.findAllByEnabledTrueOrderByIdAsc()).thenReturn(
                List.of(watchlist(1), watchlist(2), watchlist(3), watchlist(4), watchlist(5), watchlist(6)));
        when(dailyBriefService.generate(request(1, DATE))).thenReturn(brief(11, 1));
        when(dailyBriefService.generate(request(2, DATE))).thenReturn(brief(22, 0));
        when(dailyBriefService.generate(request(3, DATE))).thenReturn(brief(33, 1));
        when(dailyBriefService.generate(request(4, DATE))).thenReturn(brief(44, 1));
        when(dailyBriefService.generate(request(5, DATE))).thenThrow(new IllegalStateException("private-database-detail"));
        when(dailyBriefService.generate(request(6, DATE))).thenReturn(brief(66, 1));
        when(analysisService.generate(11L)).thenReturn(null);
        when(analysisService.generate(33L)).thenThrow(new DailyBriefAiProviderUnavailableException());
        when(analysisService.generate(44L)).thenThrow(new IllegalStateException("private-provider-detail"));
        when(analysisService.generate(66L)).thenReturn(null);

        ScheduledDailyBriefResult result = scheduler.runScheduledGeneration();

        assertThat(result).isEqualTo(new ScheduledDailyBriefResult(NOW, DATE,
                6, 1, 5, 1, 2, 2, 1, false, false));
        InOrder order = inOrder(dailyBriefService, analysisService);
        order.verify(dailyBriefService).generate(request(1, DATE));
        order.verify(analysisService).generate(11L);
        order.verify(dailyBriefService).generate(request(2, DATE));
        order.verify(dailyBriefService).generate(request(3, DATE));
        order.verify(analysisService).generate(33L);
        order.verify(dailyBriefService).generate(request(4, DATE));
        order.verify(analysisService).generate(44L);
        order.verify(dailyBriefService).generate(request(5, DATE));
        order.verify(dailyBriefService).generate(request(6, DATE));
        order.verify(analysisService).generate(66L);
        verifyNoMoreInteractions(dailyBriefService, analysisService);
        assertThat(output).contains("watchlistsProcessed=6, watchlistsFailed=1, briefsGenerated=5, emptyBriefs=1, aiGenerated=2, aiSkipped=2, aiFailed=1")
                .doesNotContain("private-database-detail", "private-provider-detail", "Watchlist 1");
    }

    @Test
    void noEnabledWatchlistsProducesZeroCounters() {
        when(watchlistRepository.findAllByEnabledTrueOrderByIdAsc()).thenReturn(List.of());

        assertThat(scheduler.runScheduledGeneration()).isEqualTo(new ScheduledDailyBriefResult(
                NOW, DATE, 0, 0, 0, 0, 0, 0, 0, false, false));
        verifyNoInteractions(dailyBriefService, analysisService);
    }

    @Test
    void unexpectedRepositoryFailureReleasesOverlapGuard() {
        when(watchlistRepository.findAllByEnabledTrueOrderByIdAsc())
                .thenThrow(new IllegalStateException("private-query-detail"))
                .thenReturn(List.of());

        assertThat(scheduler.runScheduledGeneration().schedulerFailed()).isTrue();
        ScheduledDailyBriefResult next = scheduler.runScheduledGeneration();
        assertThat(next.schedulerFailed()).isFalse();
        assertThat(next.skippedOverlap()).isFalse();
    }

    @Test
    void concurrentInvocationIsSkippedAndLaterRunCanProceed() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(watchlistRepository.findAllByEnabledTrueOrderByIdAsc()).thenReturn(List.of(watchlist(1)));
        when(dailyBriefService.generate(any())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test release timed out");
            }
            return brief(11, 0);
        });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var activeRun = executor.submit(scheduler::runScheduledGeneration);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                ScheduledDailyBriefResult overlappingRun = scheduler.runScheduledGeneration();
                assertThat(overlappingRun.skippedOverlap()).isTrue();
                assertThat(overlappingRun.watchlistsProcessed()).isZero();
                verify(watchlistRepository).findAllByEnabledTrueOrderByIdAsc();
            } finally {
                release.countDown();
            }
            assertThat(activeRun.get(5, TimeUnit.SECONDS).briefsGenerated()).isEqualTo(1);
        }
        assertThat(scheduler.runScheduledGeneration().skippedOverlap()).isFalse();
        verify(dailyBriefService, times(2)).generate(request(1, DATE));
        verifyNoInteractions(analysisService);
    }

    @Test
    void scheduledCallbackDelegatesToSameOrchestration() {
        when(watchlistRepository.findAllByEnabledTrueOrderByIdAsc()).thenReturn(List.of(watchlist(1)));
        when(dailyBriefService.generate(request(1, DATE))).thenReturn(brief(11, 1));

        scheduler.triggerScheduledGeneration();

        verify(dailyBriefService).generate(request(1, DATE));
        verify(analysisService).generate(11L);
    }

    private DailyBriefScheduler scheduler(String zone) {
        return scheduler(zone, NOW, -1);
    }

    private DailyBriefScheduler scheduler(String zone, Instant now, int dayOffset) {
        return new DailyBriefScheduler(watchlistRepository, dailyBriefService, analysisService,
                new DailyBriefSchedulerProperties(true, "0 40 10 * * *", zone, dayOffset),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Watchlist watchlist(long id) {
        Watchlist watchlist = new Watchlist("Watchlist " + id);
        ReflectionTestUtils.setField(watchlist, "id", id);
        return watchlist;
    }

    private static GenerateDailyBriefRequest request(long id, LocalDate date) {
        return new GenerateDailyBriefRequest(id, date, null);
    }

    private static DailyBriefResponse brief(long id, int count) {
        return new DailyBriefResponse(id, id, "Watchlist", DATE, "Asia/Shanghai", NOW, NOW,
                count, count, NOW, NOW, List.of());
    }
}
