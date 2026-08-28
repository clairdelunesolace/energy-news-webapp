package com.carya.energynews.dailybrief;

import com.carya.energynews.watchlist.WatchlistNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DailyBriefController.class)
@AutoConfigureMockMvc(addFilters = false)
class DailyBriefControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DailyBriefService dailyBriefService;

    @Test
    void generatesAndReturnsInspectableMetadata() throws Exception {
        GenerateDailyBriefRequest request = new GenerateDailyBriefRequest(
                4L,
                LocalDate.parse("2026-08-28"),
                5
        );
        when(dailyBriefService.generate(request)).thenReturn(response());

        mockMvc.perform(post("/api/daily-briefs/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "watchlistId": 4,
                                  "date": "2026-08-28",
                                  "maxItems": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.watchlistName").value("Storage"))
                .andExpect(jsonPath("$.zone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.windowStart").value("2026-08-27T16:00:00Z"))
                .andExpect(jsonPath("$.windowEnd").value("2026-08-28T16:00:00Z"))
                .andExpect(jsonPath("$.candidateCount").value(3))
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.items[0].title").value("中文标题"))
                .andExpect(jsonPath("$.items[0].matchedKeywords[0]").value("battery storage"))
                .andExpect(jsonPath("$.items[0].content").doesNotExist());

        verify(dailyBriefService).generate(request);
    }

    @Test
    void validatesRequiredWatchlistAndMaxItemsRange() throws Exception {
        mockMvc.perform(post("/api/daily-briefs/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxItems\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.watchlistId").value("must not be null"))
                .andExpect(jsonPath("$.errors.maxItems")
                        .value("must be greater than or equal to 1"));

        mockMvc.perform(post("/api/daily-briefs/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchlistId\":4,\"maxItems\":21}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.maxItems")
                        .value("must be less than or equal to 20"));

        verifyNoInteractions(dailyBriefService);
    }

    @Test
    void retrievesByIdAndByWatchlistDate() throws Exception {
        when(dailyBriefService.getById(9L)).thenReturn(response());
        when(dailyBriefService.getByWatchlistAndDate(4L, LocalDate.parse("2026-08-28")))
                .thenReturn(response());

        mockMvc.perform(get("/api/daily-briefs/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9));

        mockMvc.perform(get("/api/daily-briefs")
                        .param("watchlistId", "4")
                        .param("date", "2026-08-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefDate").value("2026-08-28"));
    }

    @Test
    void mapsUnknownDisabledAndMissingBriefProblems() throws Exception {
        when(dailyBriefService.generate(any(GenerateDailyBriefRequest.class)))
                .thenThrow(new WatchlistNotFoundException(99L))
                .thenThrow(new DailyBriefWatchlistDisabledException(4L));
        when(dailyBriefService.getById(99L)).thenThrow(new DailyBriefNotFoundException(99L));

        mockMvc.perform(post("/api/daily-briefs/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchlistId\":99}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Watchlist not found"));

        mockMvc.perform(post("/api/daily-briefs/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchlistId\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Watchlist is disabled"));

        mockMvc.perform(get("/api/daily-briefs/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Daily brief not found"));
    }

    static DailyBriefResponse response() {
        return new DailyBriefResponse(
                9L,
                4L,
                "Storage",
                LocalDate.parse("2026-08-28"),
                "Asia/Shanghai",
                Instant.parse("2026-08-27T16:00:00Z"),
                Instant.parse("2026-08-28T16:00:00Z"),
                3,
                1,
                Instant.parse("2026-08-28T12:00:00Z"),
                Instant.parse("2026-08-28T12:00:00Z"),
                List.of(new DailyBriefItemResponse(
                        1,
                        12L,
                        "中文标题",
                        "Original description",
                        "https://publisher.example/article",
                        "Publisher",
                        Instant.parse("2026-08-28T10:00:00Z"),
                        Instant.parse("2026-08-28T10:00:00Z"),
                        1,
                        List.of("battery storage")
                ))
        );
    }
}
