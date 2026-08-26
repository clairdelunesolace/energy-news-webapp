package com.carya.energynews.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SourceController.class)
@AutoConfigureMockMvc(addFilters = false)
class SourceControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-19T01:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-19T02:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SourceService sourceService;

    @Test
    void returnsAllSources() throws Exception {
        when(sourceService.getAll()).thenReturn(List.of(sourceResponse()));

        mockMvc.perform(get("/api/sources"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Energy Storage News"))
                .andExpect(jsonPath("$[0].type").value("RSS"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[0].language").value("EN"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].contentEnrichmentEnabled").value(false));
    }

    @Test
    void returnsSourceById() throws Exception {
        when(sourceService.getById(1L)).thenReturn(sourceResponse());

        mockMvc.perform(get("/api/sources/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.language").value("EN"))
                .andExpect(jsonPath("$.contentEnrichmentEnabled").value(false))
                .andExpect(jsonPath("$.url").value("https://example.com/feed"));
    }

    @Test
    void returnsNotFoundProblemWhenSourceDoesNotExist() throws Exception {
        when(sourceService.getById(99L)).thenThrow(new SourceNotFoundException(99L));

        mockMvc.perform(get("/api/sources/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Source not found"))
                .andExpect(jsonPath("$.detail").value("Source with id 99 was not found"));
    }

    @Test
    void createsSource() throws Exception {
        CreateSourceRequest request = new CreateSourceRequest(
                "Energy Storage News",
                "https://example.com/feed",
                SourceType.RSS,
                SourcePriority.HIGH
        );
        when(sourceService.create(request)).thenReturn(sourceResponse());

        mockMvc.perform(post("/api/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Energy Storage News",
                                  "url": "https://example.com/feed",
                                  "type": "RSS",
                                  "priority": "HIGH"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.language").value("EN"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.contentEnrichmentEnabled").value(false))
                .andExpect(jsonPath("$.createdAt").value("2026-08-19T01:00:00Z"));

        verify(sourceService).create(request);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void createsSourceWithExplicitContentEnrichmentSetting(
            boolean contentEnrichmentEnabled
    ) throws Exception {
        CreateSourceRequest request = new CreateSourceRequest(
                "Qualified source",
                "https://example.com/qualified-feed",
                SourceType.RSS,
                SourcePriority.HIGH,
                SourceLanguage.EN,
                contentEnrichmentEnabled
        );
        when(sourceService.create(request)).thenReturn(sourceResponse(
                SourceLanguage.EN,
                contentEnrichmentEnabled
        ));

        mockMvc.perform(post("/api/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Qualified source",
                                  "url": "https://example.com/qualified-feed",
                                  "type": "RSS",
                                  "priority": "HIGH",
                                  "language": "EN",
                                  "contentEnrichmentEnabled": %s
                                }
                                """.formatted(contentEnrichmentEnabled)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentEnrichmentEnabled")
                        .value(contentEnrichmentEnabled));

        verify(sourceService).create(request);
    }

    @Test
    void createsSourceWithExplicitChineseLanguage() throws Exception {
        CreateSourceRequest request = new CreateSourceRequest(
                "Example Chinese Source",
                "https://example.cn/feed",
                SourceType.RSS,
                SourcePriority.MEDIUM,
                SourceLanguage.ZH_CN
        );
        when(sourceService.create(request)).thenReturn(sourceResponse(SourceLanguage.ZH_CN));

        mockMvc.perform(post("/api/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Example Chinese Source",
                                  "url": "https://example.cn/feed",
                                  "type": "RSS",
                                  "priority": "MEDIUM",
                                  "language": "ZH_CN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.language").value("ZH_CN"));

        verify(sourceService).create(request);
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "url": "",
                                  "type": null,
                                  "priority": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").value("must not be blank"))
                .andExpect(jsonPath("$.errors.url").value("must not be blank"))
                .andExpect(jsonPath("$.errors.type").value("must not be null"))
                .andExpect(jsonPath("$.errors.priority").value("must not be null"));

        verifyNoInteractions(sourceService);
    }

    @Test
    void returnsConflictProblemForDuplicateUrl() throws Exception {
        when(sourceService.create(any(CreateSourceRequest.class)))
                .thenThrow(new DuplicateSourceUrlException("https://example.com/feed"));

        mockMvc.perform(post("/api/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Duplicate",
                                  "url": "https://example.com/feed",
                                  "type": "API",
                                  "priority": "MEDIUM"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Duplicate source URL"))
                .andExpect(jsonPath("$.detail")
                        .value("A source with URL 'https://example.com/feed' already exists"));
    }

    private static SourceResponse sourceResponse() {
        return sourceResponse(SourceLanguage.EN, false);
    }

    private static SourceResponse sourceResponse(SourceLanguage language) {
        return sourceResponse(language, false);
    }

    private static SourceResponse sourceResponse(
            SourceLanguage language,
            boolean contentEnrichmentEnabled
    ) {
        return new SourceResponse(
                1L,
                "Energy Storage News",
                "https://example.com/feed",
                SourceType.RSS,
                SourcePriority.HIGH,
                language,
                true,
                contentEnrichmentEnabled,
                CREATED_AT,
                UPDATED_AT
        );
    }
}
