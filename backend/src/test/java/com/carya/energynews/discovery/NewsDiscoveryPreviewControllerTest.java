package com.carya.energynews.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewsDiscoveryPreviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        NewsDiscoveryQueryFactory.class,
        NewsDiscoveryPreviewControllerTest.FixedClockConfiguration.class
})
class NewsDiscoveryPreviewControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:15:30Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewsDiscoveryService discoveryService;

    @Test
    void returnsProviderNeutralPreviewWithoutPersistence() throws Exception {
        DiscoveredArticle article = new DiscoveredArticle(
                "Grid battery project",
                "https://example.com/grid-battery",
                "Project description",
                "Example News",
                Instant.parse("2026-08-26T10:00:00Z")
        );
        when(discoveryService.providerName()).thenReturn("brave-news");
        when(discoveryService.discover(any())).thenReturn(List.of(article));

        mockMvc.perform(get("/api/discovery/preview")
                        .param("keyword", "  battery energy storage  ")
                        .param("from", "2026-08-25")
                        .param("to", "2026-08-26")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.provider").value("brave-news"))
                .andExpect(jsonPath("$.keyword").value("battery energy storage"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].title").value("Grid battery project"))
                .andExpect(jsonPath("$.results[0].url").value("https://example.com/grid-battery"))
                .andExpect(jsonPath("$.results[0].description").value("Project description"))
                .andExpect(jsonPath("$.results[0].sourceName").value("Example News"))
                .andExpect(jsonPath("$.results[0].publishedAt")
                        .value("2026-08-26T10:00:00Z"));

        verify(discoveryService).discover(new NewsDiscoveryQuery(
                "battery energy storage",
                Instant.parse("2026-08-25T00:00:00Z"),
                Instant.parse("2026-08-26T23:59:59.999999999Z"),
                20
        ));
    }

    @Test
    void capsCurrentDateUpperBoundAtCurrentInstant() throws Exception {
        when(discoveryService.providerName()).thenReturn("gnews");
        when(discoveryService.discover(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/discovery/preview")
                        .param("keyword", "BESS")
                        .param("to", "2026-08-27"))
                .andExpect(status().isOk());

        verify(discoveryService).discover(new NewsDiscoveryQuery(
                "BESS",
                null,
                NOW,
                20
        ));
    }

    @Test
    void capsFutureDateUpperBoundAtCurrentInstant() throws Exception {
        when(discoveryService.providerName()).thenReturn("gnews");
        when(discoveryService.discover(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/discovery/preview")
                        .param("keyword", "BESS")
                        .param("to", "2026-08-30"))
                .andExpect(status().isOk());

        verify(discoveryService).discover(new NewsDiscoveryQuery(
                "BESS",
                null,
                NOW,
                20
        ));
    }

    @Test
    void returnsBadRequestForInvalidNeutralLimit() throws Exception {
        mockMvc.perform(get("/api/discovery/preview")
                        .param("keyword", "BESS")
                        .param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid discovery query"))
                .andExpect(jsonPath("$.detail")
                        .value("Discovery limit must be between 1 and 100"));
    }

    @Test
    void returnsBadGatewayProblemForProviderFailure() throws Exception {
        when(discoveryService.discover(any()))
                .thenThrow(new NewsDiscoveryException("brave-news was rate limited (HTTP 429)"));

        mockMvc.perform(get("/api/discovery/preview")
                        .param("keyword", "BESS"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("News discovery failed"))
                .andExpect(jsonPath("$.detail")
                        .value("brave-news was rate limited (HTTP 429)"));
    }

    @Test
    void reportsSelectedGNewsProviderWithoutChangingEndpointShape() throws Exception {
        when(discoveryService.providerName()).thenReturn("gnews");
        when(discoveryService.discover(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/discovery/preview")
                        .param("keyword", "AI data center"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("gnews"))
                .andExpect(jsonPath("$.keyword").value("AI data center"))
                .andExpect(jsonPath("$.count").value(0));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        Clock discoveryClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
