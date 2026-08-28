package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.watchlist.WatchlistNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistDiscoveryController.class)
@AutoConfigureMockMvc(addFilters = false)
class WatchlistDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WatchlistDiscoveryService discoveryService;

    @Test
    void runsOneWatchlistWithDefaultLimitAndReturnsCounters() throws Exception {
        WatchlistDiscoveryRunResponse response = response();
        WatchlistDiscoveryRunRequest request = new WatchlistDiscoveryRunRequest(
                1L,
                LocalDate.parse("2026-08-25"),
                LocalDate.parse("2026-08-27"),
                null
        );
        when(discoveryService.run(request)).thenReturn(response);

        mockMvc.perform(post("/api/watchlist-discovery/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "watchlistId": 1,
                                  "from": "2026-08-25",
                                  "to": "2026-08-27"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.watchlistId").value(1))
                .andExpect(jsonPath("$.watchlistName").value("Industry Test"))
                .andExpect(jsonPath("$.keywordsProcessed").value(2))
                .andExpect(jsonPath("$.keywordsFailed").value(1))
                .andExpect(jsonPath("$.discovered").value(8))
                .andExpect(jsonPath("$.relevanceRejected").value(2))
                .andExpect(jsonPath("$.saved").value(3))
                .andExpect(jsonPath("$.duplicates").value(2))
                .andExpect(jsonPath("$.keywordMatchesCreated").value(4))
                .andExpect(jsonPath("$.keywordMatchesExisting").value(1))
                .andExpect(jsonPath("$.skippedUnsupportedLanguage").value(1))
                .andExpect(jsonPath("$.skippedInvalidUrl").value(0))
                .andExpect(jsonPath("$.postProcessingAttempted").value(3))
                .andExpect(jsonPath("$.metadataTranslationSucceeded").value(2))
                .andExpect(jsonPath("$.metadataTranslationFailed").value(1))
                .andExpect(jsonPath("$.contentExtractionSucceeded").value(1))
                .andExpect(jsonPath("$.contentExtractionFailed").value(2))
                .andExpect(jsonPath("$.contentTranslationSucceeded").value(1))
                .andExpect(jsonPath("$.contentTranslationFailed").value(0))
                .andExpect(jsonPath("$.failedKeywords[0].keyword").value("800VDC"))
                .andExpect(jsonPath("$.keywordResults[0].keyword").value("NVIDIA"));

        verify(discoveryService).run(request);
    }

    @Test
    void validatesRequiredIdAndLimitRange() throws Exception {
        mockMvc.perform(post("/api/watchlist-discovery/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limitPerKeyword\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.watchlistId").value("must not be null"))
                .andExpect(jsonPath("$.errors.limitPerKeyword")
                        .value("must be greater than or equal to 1"));

        mockMvc.perform(post("/api/watchlist-discovery/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchlistId\":1,\"limitPerKeyword\":21}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.limitPerKeyword")
                        .value("must be less than or equal to 20"));
        verifyNoInteractions(discoveryService);
    }

    @Test
    void mapsUnknownDisabledAndUnavailableProblems() throws Exception {
        when(discoveryService.run(any(WatchlistDiscoveryRunRequest.class)))
                .thenThrow(new WatchlistNotFoundException(99L))
                .thenThrow(new WatchlistDisabledException(1L))
                .thenThrow(new WatchlistDiscoveryProviderUnavailableException());

        mockMvc.perform(post("/api/watchlist-discovery/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchlistId\":99}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Watchlist not found"));

        mockMvc.perform(post("/api/watchlist-discovery/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchlistId\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Watchlist is disabled"));

        mockMvc.perform(post("/api/watchlist-discovery/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchlistId\":1}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Service Unavailable"));
    }

    private WatchlistDiscoveryRunResponse response() {
        return new WatchlistDiscoveryRunResponse(
                1L,
                "Industry Test",
                2,
                1,
                8,
                2,
                3,
                2,
                4,
                1,
                1,
                0,
                3,
                2,
                1,
                1,
                2,
                1,
                0,
                List.of(new WatchlistDiscoveryKeywordFailure(
                        13L,
                        "800VDC",
                        "gnews was rate limited"
                )),
                List.of(new WatchlistDiscoveryKeywordResult(
                        11L,
                        "NVIDIA",
                        4,
                        1,
                        2,
                        1,
                        2,
                        1,
                        0,
                        0,
                        null
                ))
        );
    }
}
