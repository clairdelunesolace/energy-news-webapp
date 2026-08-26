package com.carya.energynews.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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

@WebMvcTest(ArticleContentBackfillController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArticleContentBackfillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleContentBackfillService articleContentBackfillService;

    @Test
    void usesDefaultLimitAndReturnsCounters() throws Exception {
        when(articleContentBackfillService.backfill(5))
                .thenReturn(new ArticleContentBackfillResult(5, 4, 1));

        mockMvc.perform(post("/api/articles/content-backfill"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.selected").value(5))
                .andExpect(jsonPath("$.fetched").value(4))
                .andExpect(jsonPath("$.failed").value(1));

        verify(articleContentBackfillService).backfill(5);
    }

    @Test
    void acceptsACustomValidLimit() throws Exception {
        when(articleContentBackfillService.backfill(3))
                .thenReturn(new ArticleContentBackfillResult(3, 3, 0));

        mockMvc.perform(post("/api/articles/content-backfill").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selected").value(3))
                .andExpect(jsonPath("$.fetched").value(3))
                .andExpect(jsonPath("$.failed").value(0));

        verify(articleContentBackfillService).backfill(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 21})
    void returnsProblemDetailForOutOfRangeLimits(int limit) throws Exception {
        mockMvc.perform(post("/api/articles/content-backfill")
                        .param("limit", Integer.toString(limit)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title")
                        .value("Invalid article content backfill limit"))
                .andExpect(jsonPath("$.detail")
                        .value("Article content backfill limit must be between 1 and 20"));

        verifyNoInteractions(articleContentBackfillService);
    }

    @Test
    void returnsProblemDetailForANonNumericLimit() throws Exception {
        mockMvc.perform(post("/api/articles/content-backfill").param("limit", "many"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title")
                        .value("Invalid article content backfill limit"))
                .andExpect(jsonPath("$.detail")
                        .value("Article content backfill limit must be an integer between 1 and 20"));

        verifyNoInteractions(articleContentBackfillService);
    }
}
