package com.carya.energynews.dailybriefanalysis;

import com.carya.energynews.dailybrief.DailyBriefItemResponse;
import com.carya.energynews.dailybrief.DailyBriefResponse;
import com.carya.energynews.dailybrief.DailyBriefService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyBriefAnalysisProviderUnavailableTest {

    @Test
    @SuppressWarnings("unchecked")
    void noneProviderLeavesBriefReadAvailableButAiGenerationUnavailable() {
        DailyBriefService dailyBriefService = mock(DailyBriefService.class);
        DailyBriefAnalysisStore analysisStore = mock(DailyBriefAnalysisStore.class);
        ObjectProvider<DailyBriefAiProvider> providerSource = mock(ObjectProvider.class);
        when(dailyBriefService.getById(1L)).thenReturn(nonEmptyBrief());
        when(providerSource.getIfAvailable()).thenReturn(null);

        DailyBriefAnalysisService service = new DailyBriefAnalysisService(
                dailyBriefService,
                new DailyBriefAiInputFactory(),
                new DailyBriefAiResultValidator(),
                analysisStore,
                providerSource,
                Clock.fixed(Instant.parse("2026-08-28T06:00:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.generate(1L))
                .isInstanceOf(DailyBriefAiProviderUnavailableException.class);
        verifyNoInteractions(analysisStore);
    }

    private static DailyBriefResponse nonEmptyBrief() {
        Instant start = Instant.parse("2026-08-26T16:00:00Z");
        Instant end = Instant.parse("2026-08-27T16:00:00Z");
        return new DailyBriefResponse(
                1L,
                2L,
                "Storage",
                LocalDate.parse("2026-08-27"),
                "Asia/Shanghai",
                start,
                end,
                1,
                1,
                end,
                end,
                List.of(new DailyBriefItemResponse(
                        1,
                        103L,
                        "标题",
                        "描述",
                        "https://example.com/103",
                        "Publisher",
                        start,
                        start,
                        1,
                        List.of("storage")
                ))
        );
    }
}
