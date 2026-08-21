package com.carya.energynews.translation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArticleContentTranslationBackfillController.class)
class ArticleContentTranslationBackfillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleContentTranslationBackfillService backfillService;

    @Test
    void usesDefaultLimitAndReturnsCounters() throws Exception {
        when(backfillService.backfill(1))
                .thenReturn(new ArticleContentTranslationBackfillResult(1, 1, 0));

        mockMvc.perform(post("/api/translations/content-backfill"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.selected").value(1))
                .andExpect(jsonPath("$.translated").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        verify(backfillService).backfill(1);
    }

    @Test
    void acceptsCustomValidLimit() throws Exception {
        when(backfillService.backfill(5))
                .thenReturn(new ArticleContentTranslationBackfillResult(5, 4, 1));

        mockMvc.perform(post("/api/translations/content-backfill").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selected").value(5))
                .andExpect(jsonPath("$.translated").value(4))
                .andExpect(jsonPath("$.failed").value(1));

        verify(backfillService).backfill(5);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11})
    void returnsProblemDetailForOutOfRangeLimits(int limit) throws Exception {
        mockMvc.perform(post("/api/translations/content-backfill")
                        .param("limit", Integer.toString(limit)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title")
                        .value("Invalid article content translation backfill limit"))
                .andExpect(jsonPath("$.detail").value(
                        "Article content translation backfill limit must be between 1 and 10"
                ));

        verifyNoInteractions(backfillService);
    }

    @Test
    void returnsProblemDetailForNonNumericLimit() throws Exception {
        mockMvc.perform(post("/api/translations/content-backfill").param("limit", "many"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title")
                        .value("Invalid article content translation backfill limit"))
                .andExpect(jsonPath("$.detail").value(
                        "Article content translation backfill limit must be an integer between 1 and 10"
                ));

        verifyNoInteractions(backfillService);
    }
}
