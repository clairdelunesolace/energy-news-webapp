package com.carya.energynews.watchlist;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({WatchlistController.class, KeywordController.class})
@AutoConfigureMockMvc(addFilters = false)
class WatchlistControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-26T01:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-26T02:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WatchlistService watchlistService;

    @Test
    void listsAndGetsWatchlistsWithKeywords() throws Exception {
        when(watchlistService.getAll()).thenReturn(List.of(watchlistResponse()));
        when(watchlistService.getById(1L)).thenReturn(watchlistResponse());

        mockMvc.perform(get("/api/watchlists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("NVIDIA"))
                .andExpect(jsonPath("$[0].keywords[0].keyword").value("GB200"));

        mockMvc.perform(get("/api/watchlists/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.keywords[0].id").value(10));
    }

    @Test
    void createsWatchlist() throws Exception {
        when(watchlistService.create(new CreateWatchlistRequest("NVIDIA", null)))
                .thenReturn(watchlistResponse());

        mockMvc.perform(post("/api/watchlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"NVIDIA"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.createdAt").value("2026-08-26T01:00:00Z"));
    }

    @Test
    void patchesAndDeletesWatchlist() throws Exception {
        UpdateWatchlistRequest request = new UpdateWatchlistRequest("AI Infrastructure", false);
        when(watchlistService.update(1L, request)).thenReturn(new WatchlistResponse(
                1L,
                "AI Infrastructure",
                false,
                CREATED_AT,
                UPDATED_AT,
                List.of()
        ));

        mockMvc.perform(patch("/api/watchlists/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"AI Infrastructure","enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("AI Infrastructure"))
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(delete("/api/watchlists/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(watchlistService).delete(1L);
    }

    @Test
    void addsEditsAndDeletesKeyword() throws Exception {
        KeywordResponse keyword = keywordResponse();
        when(watchlistService.addKeyword(
                1L,
                new CreateKeywordRequest("GB200", null)
        )).thenReturn(keyword);
        when(watchlistService.updateKeyword(
                10L,
                new UpdateKeywordRequest("Rubin", false)
        )).thenReturn(new KeywordResponse(
                10L,
                "Rubin",
                false,
                CREATED_AT,
                UPDATED_AT
        ));

        mockMvc.perform(post("/api/watchlists/1/keywords")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyword":"GB200"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keyword").value("GB200"));

        mockMvc.perform(patch("/api/keywords/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyword":"Rubin","enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyword").value("Rubin"))
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(delete("/api/keywords/10"))
                .andExpect(status().isNoContent());
        verify(watchlistService).deleteKeyword(10L);
    }

    @Test
    void rejectsBlankAndOversizedValues() throws Exception {
        mockMvc.perform(post("/api/watchlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").value("must not be blank"));

        mockMvc.perform(patch("/api/watchlists/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("must not be blank"));

        mockMvc.perform(post("/api/watchlists/1/keywords")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyword":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.keyword").value("must not be blank"));

        mockMvc.perform(patch("/api/keywords/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyword":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.keyword").value("must not be blank"));

        verifyNoInteractions(watchlistService);
    }

    @Test
    void returnsNotFoundProblemsForUnknownIds() throws Exception {
        when(watchlistService.getById(99L)).thenThrow(new WatchlistNotFoundException(99L));
        when(watchlistService.updateKeyword(any(Long.class), any(UpdateKeywordRequest.class)))
                .thenThrow(new KeywordNotFoundException(99L));

        mockMvc.perform(get("/api/watchlists/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Watchlist not found"))
                .andExpect(jsonPath("$.detail").value("Watchlist with id 99 was not found"));

        mockMvc.perform(patch("/api/keywords/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":false}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Keyword not found"));
    }

    @Test
    void returnsConflictProblemsForDuplicates() throws Exception {
        when(watchlistService.create(any(CreateWatchlistRequest.class)))
                .thenThrow(new DuplicateWatchlistNameException("NVIDIA"));
        when(watchlistService.addKeyword(any(Long.class), any(CreateKeywordRequest.class)))
                .thenThrow(new DuplicateKeywordException("GB200"));

        mockMvc.perform(post("/api/watchlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"NVIDIA"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate watchlist name"));

        mockMvc.perform(post("/api/watchlists/1/keywords")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyword":"GB200"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate keyword"))
                .andExpect(jsonPath("$.detail")
                        .value("Keyword 'GB200' already exists in this watchlist"));
    }

    private static WatchlistResponse watchlistResponse() {
        return new WatchlistResponse(
                1L,
                "NVIDIA",
                true,
                CREATED_AT,
                UPDATED_AT,
                List.of(keywordResponse())
        );
    }

    private static KeywordResponse keywordResponse() {
        return new KeywordResponse(10L, "GB200", true, CREATED_AT, UPDATED_AT);
    }
}
