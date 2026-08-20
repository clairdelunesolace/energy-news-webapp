package com.carya.energynews.sync;

import com.carya.energynews.collection.NewsCollectionException;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewsSyncController.class)
class NewsSyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewsSyncService newsSyncService;

    @MockitoBean
    private SourceRepository sourceRepository;

    @Test
    void syncsAllEnabledSources() throws Exception {
        NewsSyncResult result = new NewsSyncResult(5, 1, 2, 2, 3, 1, 1);
        when(newsSyncService.syncAllEnabledSources()).thenReturn(result);

        mockMvc.perform(post("/api/news-sync"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.collected").value(5))
                .andExpect(jsonPath("$.filteredOut").value(1))
                .andExpect(jsonPath("$.saved").value(2))
                .andExpect(jsonPath("$.duplicates").value(2))
                .andExpect(jsonPath("$.translated").value(3))
                .andExpect(jsonPath("$.translationFailed").value(1))
                .andExpect(jsonPath("$.failedSources").value(1));

        verify(newsSyncService).syncAllEnabledSources();
    }

    @Test
    void syncsOneSource() throws Exception {
        Source source = source();
        NewsSyncResult result = new NewsSyncResult(2, 1, 1, 0, 1, 0, 0);
        when(sourceRepository.findById(7L)).thenReturn(Optional.of(source));
        when(newsSyncService.sync(source)).thenReturn(result);

        mockMvc.perform(post("/api/news-sync/sources/7"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.collected").value(2))
                .andExpect(jsonPath("$.filteredOut").value(1))
                .andExpect(jsonPath("$.saved").value(1))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andExpect(jsonPath("$.translated").value(1))
                .andExpect(jsonPath("$.translationFailed").value(0))
                .andExpect(jsonPath("$.failedSources").value(0));

        verify(sourceRepository).findById(7L);
        verify(newsSyncService).sync(source);
    }

    @Test
    void returnsNotFoundProblemWhenSourceDoesNotExist() throws Exception {
        when(sourceRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/news-sync/sources/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Source not found"))
                .andExpect(jsonPath("$.detail").value("Source with id 99 was not found"));

        verify(sourceRepository).findById(99L);
        verifyNoInteractions(newsSyncService);
    }

    @Test
    void returnsBadGatewayProblemWhenCollectionFails() throws Exception {
        Source source = source();
        when(sourceRepository.findById(7L)).thenReturn(Optional.of(source));
        when(newsSyncService.sync(source))
                .thenThrow(new NewsCollectionException("Unable to fetch RSS feed"));

        mockMvc.perform(post("/api/news-sync/sources/7"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("News collection failed"))
                .andExpect(jsonPath("$.detail").value("Unable to fetch RSS feed"));
    }

    private Source source() {
        return new Source(
                "Energy Storage News",
                "https://example.com/feed",
                SourceType.RSS,
                SourcePriority.HIGH
        );
    }
}
