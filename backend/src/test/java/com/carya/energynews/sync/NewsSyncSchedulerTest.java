package com.carya.energynews.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class NewsSyncSchedulerTest {

    @Mock
    private NewsSyncService newsSyncService;

    @InjectMocks
    private NewsSyncScheduler newsSyncScheduler;

    @Test
    void usesConfigurableCronAndTimeZone() throws NoSuchMethodException {
        Scheduled scheduled = NewsSyncScheduler.class
                .getMethod("runScheduledSync")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${app.news-sync.cron}");
        assertThat(scheduled.zone()).isEqualTo("${app.news-sync.zone}");
    }

    @Test
    void delegatesScheduledSynchronizationAndLogsAllStageCounters(CapturedOutput output) {
        when(newsSyncService.syncAllEnabledSources())
                .thenReturn(new NewsSyncResult(5, 1, 2, 2, 3, 1, 4, 1, 2, 1, 1));

        newsSyncScheduler.runScheduledSync();

        verify(newsSyncService).syncAllEnabledSources();
        assertThat(output).contains(
                "translated=3, translationFailed=1, contentFetched=4, contentFetchFailed=1, "
                        + "contentTranslated=2, contentTranslationFailed=1, failedSources=1"
        );
    }

    @Test
    void handlesUnexpectedSynchronizationFailure() {
        when(newsSyncService.syncAllEnabledSources())
                .thenThrow(new IllegalStateException("Database unavailable"));

        assertThatCode(newsSyncScheduler::runScheduledSync)
                .doesNotThrowAnyException();
        verify(newsSyncService).syncAllEnabledSources();
    }
}
