package com.carya.energynews.translation;

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

@WebMvcTest(TranslationBackfillController.class)
@AutoConfigureMockMvc(addFilters = false)
class TranslationBackfillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TranslationBackfillService translationBackfillService;

    @Test
    void usesDefaultLimitAndReturnsBackfillCounters() throws Exception {
        when(translationBackfillService.backfill(20))
                .thenReturn(new TranslationBackfillResult(20, 18, 2));

        mockMvc.perform(post("/api/translations/backfill"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.selected").value(20))
                .andExpect(jsonPath("$.translated").value(18))
                .andExpect(jsonPath("$.failed").value(2));

        verify(translationBackfillService).backfill(20);
    }

    @Test
    void acceptsCustomValidLimit() throws Exception {
        when(translationBackfillService.backfill(5))
                .thenReturn(new TranslationBackfillResult(5, 5, 0));

        mockMvc.perform(post("/api/translations/backfill").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selected").value(5))
                .andExpect(jsonPath("$.translated").value(5))
                .andExpect(jsonPath("$.failed").value(0));

        verify(translationBackfillService).backfill(5);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void returnsProblemDetailForInvalidLimit(int limit) throws Exception {
        mockMvc.perform(post("/api/translations/backfill")
                        .param("limit", Integer.toString(limit)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid translation backfill limit"))
                .andExpect(jsonPath("$.detail")
                        .value("Translation backfill limit must be between 1 and 100"));

        verifyNoInteractions(translationBackfillService);
    }

    @Test
    void returnsProblemDetailForNonNumericLimit() throws Exception {
        mockMvc.perform(post("/api/translations/backfill").param("limit", "many"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid translation backfill limit"))
                .andExpect(jsonPath("$.detail")
                        .value("Translation backfill limit must be an integer between 1 and 100"));

        verifyNoInteractions(translationBackfillService);
    }
}
