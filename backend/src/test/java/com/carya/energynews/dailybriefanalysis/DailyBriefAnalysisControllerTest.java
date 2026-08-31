package com.carya.energynews.dailybriefanalysis;

import com.carya.energynews.dailybrief.DailyBriefNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DailyBriefAnalysisController.class)
@AutoConfigureMockMvc(addFilters = false)
class DailyBriefAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DailyBriefAnalysisService analysisService;

    @Test
    void generatesAndReadsStructuredAnalysisWithoutReasoning() throws Exception {
        when(analysisService.generate(1L)).thenReturn(response());
        when(analysisService.get(1L)).thenReturn(response());

        mockMvc.perform(post("/api/daily-briefs/1/analysis/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.dailyBriefId").value(1))
                .andExpect(jsonPath("$.provider").value("groq"))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-20b"))
                .andExpect(jsonPath("$.headline").value("管理层标题"))
                .andExpect(jsonPath("$.events[0].rank").value(1))
                .andExpect(jsonPath("$.events[0].supportingArticleIds[0]").value(103))
                .andExpect(jsonPath("$.reasoning").doesNotExist())
                .andExpect(jsonPath("$.prompt").doesNotExist())
                .andExpect(jsonPath("$.rawResponse").doesNotExist());

        mockMvc.perform(get("/api/daily-briefs/1/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("管理层标题"));
    }

    @Test
    void mapsNotFoundEmptyUnavailableAndInvalidProviderResults() throws Exception {
        when(analysisService.generate(99L)).thenThrow(new DailyBriefNotFoundException(99L));
        when(analysisService.generate(2L)).thenThrow(new DailyBriefEmptyAnalysisException(2L));
        when(analysisService.generate(3L))
                .thenThrow(new DailyBriefAiProviderUnavailableException());
        when(analysisService.generate(4L)).thenThrow(new DailyBriefAiValidationException(
                "AI event references an Article outside the DailyBrief snapshot"
        ));
        when(analysisService.generate(5L)).thenThrow(new DailyBriefAiException(
                DailyBriefAiException.Failure.RATE_LIMITED,
                "Groq daily brief request was rate limited"
        ));
        when(analysisService.get(6L)).thenThrow(new DailyBriefAnalysisNotFoundException(6L));

        mockMvc.perform(post("/api/daily-briefs/99/analysis/generate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Daily brief not found"));
        mockMvc.perform(post("/api/daily-briefs/2/analysis/generate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Daily brief has no evidence to analyze"));
        mockMvc.perform(post("/api/daily-briefs/3/analysis/generate"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Daily brief AI provider unavailable"));
        mockMvc.perform(post("/api/daily-briefs/4/analysis/generate"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Daily brief AI response was invalid"));
        mockMvc.perform(post("/api/daily-briefs/5/analysis/generate"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Groq daily brief request was rate limited"));
        mockMvc.perform(get("/api/daily-briefs/6/analysis"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Daily brief AI analysis not found"));
    }

    static DailyBriefAnalysisResponse response() {
        return new DailyBriefAnalysisResponse(
                7L,
                1L,
                "groq",
                "openai/gpt-oss-20b",
                "管理层标题",
                "整体概览。",
                Instant.parse("2026-08-28T06:00:00Z"),
                Instant.parse("2026-08-28T06:00:00Z"),
                Instant.parse("2026-08-28T06:00:00Z"),
                List.of(new DailyBriefEventResponse(
                        1,
                        "事件标题",
                        "事件摘要",
                        "为什么重要",
                        List.of(103L, 104L)
                ))
        );
    }

    @ParameterizedTest
    @CsvSource({
            "AUTHENTICATION,502",
            "AUTHORIZATION,502",
            "INVALID_REQUEST,502",
            "RATE_LIMITED,503",
            "UPSTREAM,502",
            "MALFORMED_RESPONSE,502",
            "TIMEOUT,504"
    })
    void mapsProviderFailureCategoriesSafely(DailyBriefAiException.Failure failure, int statusCode)
            throws Exception {
        when(analysisService.generate(1L))
                .thenThrow(new DailyBriefAiException(failure, "Safe provider failure"));

        mockMvc.perform(post("/api/daily-briefs/1/analysis/generate"))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.detail").value("Safe provider failure"));
    }
}
