package com.carya.energynews.article;

import com.carya.energynews.source.SourceNotFoundException;
import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.translation.TranslationLanguage;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArticleController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArticleControllerTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-18T12:00:00Z");
    private static final Instant COLLECTED_AT = Instant.parse("2026-08-19T06:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-08-19T06:00:01Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-19T06:00:01Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleService articleService;

    @MockitoBean
    private ArticlePostProcessingBackfillService articlePostProcessingBackfillService;

    @Test
    void returnsDefaultArticlePage() throws Exception {
        when(articleService.getAll(0, 20, null, null, null)).thenReturn(new ArticlePageResponse(
                List.of(articleResponse()),
                0,
                20,
                1,
                1,
                true,
                true
        ));

        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].source.id").value(7))
                .andExpect(jsonPath("$.content[0].source.name").value("Energy Storage News"))
                .andExpect(jsonPath("$.content[0].original.language").value("EN"))
                .andExpect(jsonPath("$.content[0].original.title").value("Battery storage expands"))
                .andExpect(jsonPath("$.content[0].translation.language").value("ZH_CN"))
                .andExpect(jsonPath("$.content[0].translation.title").value("电池储能扩张"))
                .andExpect(jsonPath("$.content[0].translation.content").value("中文完整正文"))
                .andExpect(jsonPath("$.content[0].tags.length()").value(1))
                .andExpect(jsonPath("$.content[0].tags[0]").value("battery storage"))
                .andExpect(jsonPath("$.content[0].displayTitle").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        verify(articleService).getAll(0, 20, null, null, null);
    }

    @Test
    void acceptsCustomPageAndSize() throws Exception {
        when(articleService.getAll(2, 5, null, null, null)).thenReturn(new ArticlePageResponse(
                List.of(),
                2,
                5,
                14,
                3,
                false,
                true
        ));

        mockMvc.perform(get("/api/articles?page=2&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(14))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));

        verify(articleService).getAll(2, 5, null, null, null);
    }

    @Test
    void forwardsOptionalSourceAndKeywordFilters() throws Exception {
        when(articleService.getAll(1, 10, 3L, "Battery", null)).thenReturn(new ArticlePageResponse(
                List.of(),
                1,
                10,
                0,
                0,
                false,
                true
        ));

        mockMvc.perform(get("/api/articles")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sourceId", "3")
                        .param("keyword", "Battery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(articleService).getAll(1, 10, 3L, "Battery", null);
    }

    @Test
    void acceptsKeywordIdWithoutChangingArticleResponse() throws Exception {
        when(articleService.getAll(0, 20, null, null, 42L)).thenReturn(new ArticlePageResponse(
                List.of(articleResponse()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/articles").param("keywordId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].source.name").value("Energy Storage News"))
                .andExpect(jsonPath("$.content[0].original.title").value("Battery storage expands"))
                .andExpect(jsonPath("$.content[0].translation.title").value("电池储能扩张"));

        verify(articleService).getAll(0, 20, null, null, 42L);
    }

    @Test
    void combinesKeywordIdWithExistingSearchSourceAndPagination() throws Exception {
        when(articleService.getAll(1, 5, 7L, "Battery", 42L)).thenReturn(new ArticlePageResponse(
                List.of(), 1, 5, 0, 0, false, true));

        mockMvc.perform(get("/api/articles?page=1&size=5&sourceId=7&keyword=Battery&keywordId=42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(1));

        verify(articleService).getAll(1, 5, 7L, "Battery", 42L);
    }

    @Test
    void rejectsNonNumericKeywordId() throws Exception {
        mockMvc.perform(get("/api/articles?keywordId=not-an-id"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(articleService);
    }

    @Test
    void rejectsPaginationOutsideAllowedBounds() throws Exception {
        mockMvc.perform(get("/api/articles?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid pagination"));

        mockMvc.perform(get("/api/articles?size=0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/articles?size=5000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "Page must be at least 0 and size must be between 1 and 100"
                ));

        verifyNoInteractions(articleService);
    }

    @Test
    void returnsArticleById() throws Exception {
        when(articleService.getById(1L)).thenReturn(articleResponse());

        mockMvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.url").value("https://example.com/articles/storage-expands"))
                .andExpect(jsonPath("$.collectedAt").value("2026-08-19T06:00:00Z"))
                .andExpect(jsonPath("$.publishedAt").value("2026-08-18T12:00:00Z"))
                .andExpect(jsonPath("$.createdAt").value("2026-08-19T06:00:01Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-08-19T06:00:01Z"))
                .andExpect(jsonPath("$.source.id").value(7))
                .andExpect(jsonPath("$.source.name").value("Energy Storage News"))
                .andExpect(jsonPath("$.original.language").value("EN"))
                .andExpect(jsonPath("$.original.title").value("Battery storage expands"))
                .andExpect(jsonPath("$.original.description").value("Article summary"))
                .andExpect(jsonPath("$.original.content").value("Article content"))
                .andExpect(jsonPath("$.translation.language").value("ZH_CN"))
                .andExpect(jsonPath("$.translation.title").value("电池储能扩张"))
                .andExpect(jsonPath("$.translation.description").value("中文摘要"))
                .andExpect(jsonPath("$.tags.length()").value(1))
                .andExpect(jsonPath("$.tags[0]").value("battery storage"))
                .andExpect(jsonPath("$.translation.content").value("中文完整正文"));
    }

    @Test
    void serializesUnmatchedTagsAsAnEmptyArrayInListAndDetail() throws Exception {
        ArticleResponse unmatched = articleResponse(List.of());
        when(articleService.getById(1L)).thenReturn(unmatched);
        when(articleService.getAll(0, 20, null, null, null)).thenReturn(new ArticlePageResponse(
                List.of(unmatched), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.tags").isEmpty());
        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tags").isArray())
                .andExpect(jsonPath("$.content[0].tags").isEmpty());
    }

    @Test
    void backfillsPostProcessingForExactlyOneRequestedArticle() throws Exception {
        when(articlePostProcessingBackfillService.backfill(41L)).thenReturn(
                backfillResponse(ArticlePostProcessingBackfillResponse.OverallStatus.SUCCESS)
        );

        mockMvc.perform(post("/api/articles/post-processing/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"articleIds":[41]}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(41))
                .andExpect(jsonPath("$.metadataTranslationStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.contentExtractionStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.contentTranslationStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.overallStatus").value("SUCCESS"));

        verify(articlePostProcessingBackfillService).backfill(41L);
    }

    @Test
    void rejectsBackfillRequestsThatAreNotSingleArticleRecovery() throws Exception {
        mockMvc.perform(post("/api/articles/post-processing/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"articleIds":[41,42]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.articleIds")
                        .value("must contain exactly one article id"));

        verifyNoInteractions(articlePostProcessingBackfillService);
    }

    @Test
    void returnsPartialSuccessAsOk() throws Exception {
        when(articlePostProcessingBackfillService.backfill(41L)).thenReturn(
                new ArticlePostProcessingBackfillResponse(
                        41L,
                        ArticlePostProcessingBackfillResponse.StepStatus.SUCCESS,
                        ArticlePostProcessingBackfillResponse.StepStatus.FAILED,
                        ArticlePostProcessingBackfillResponse.StepStatus.NOT_AVAILABLE,
                        ArticlePostProcessingBackfillResponse.OverallStatus.PARTIAL_SUCCESS
                )
        );

        mockMvc.perform(post("/api/articles/post-processing/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"articleIds":[41]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadataTranslationStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.contentExtractionStatus").value("FAILED"))
                .andExpect(jsonPath("$.contentTranslationStatus").value("NOT_AVAILABLE"))
                .andExpect(jsonPath("$.overallStatus").value("PARTIAL_SUCCESS"));
    }

    @Test
    void mapsMetadataTranslationFailureToStructuredBadGateway() throws Exception {
        when(articlePostProcessingBackfillService.backfill(41L)).thenReturn(
                new ArticlePostProcessingBackfillResponse(
                        41L,
                        ArticlePostProcessingBackfillResponse.StepStatus.FAILED,
                        ArticlePostProcessingBackfillResponse.StepStatus.SUCCESS,
                        ArticlePostProcessingBackfillResponse.StepStatus.NOT_AVAILABLE,
                        ArticlePostProcessingBackfillResponse.OverallStatus.FAILED
                )
        );

        mockMvc.perform(post("/api/articles/post-processing/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"articleIds":[41]}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.articleId").value(41))
                .andExpect(jsonPath("$.metadataTranslationStatus").value("FAILED"))
                .andExpect(jsonPath("$.overallStatus").value("FAILED"));
    }

    @Test
    void returnsNotFoundProblemWhenArticleDoesNotExist() throws Exception {
        when(articleService.getById(99L)).thenThrow(new ArticleNotFoundException(99L));

        mockMvc.perform(get("/api/articles/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Article not found"))
                .andExpect(jsonPath("$.detail").value("Article with id 99 was not found"));
    }

    @Test
    void createsArticle() throws Exception {
        CreateArticleRequest request = new CreateArticleRequest(
                "Battery storage expands",
                "https://example.com/articles/storage-expands",
                "Article summary",
                "Article content",
                PUBLISHED_AT,
                7L
        );
        when(articleService.create(request)).thenReturn(createArticleResponse());

        mockMvc.perform(post("/api/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Battery storage expands",
                                  "url": "https://example.com/articles/storage-expands",
                                  "description": "Article summary",
                                  "content": "Article content",
                                  "publishedAt": "2026-08-18T12:00:00Z",
                                  "sourceId": 7
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.collectedAt").value("2026-08-19T06:00:00Z"))
                .andExpect(jsonPath("$.sourceId").value(7))
                .andExpect(jsonPath("$.sourceName").value("Energy Storage News"));

        verify(articleService).create(request);
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " ",
                                  "url": "",
                                  "sourceId": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.title").value("must not be blank"))
                .andExpect(jsonPath("$.errors.url").value("must not be blank"))
                .andExpect(jsonPath("$.errors.sourceId").value("must not be null"));

        verifyNoInteractions(articleService);
    }

    @Test
    void returnsNotFoundProblemWhenRequestedSourceDoesNotExist() throws Exception {
        when(articleService.create(any(CreateArticleRequest.class)))
                .thenThrow(new SourceNotFoundException(99L));

        mockMvc.perform(post("/api/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Article",
                                  "url": "https://example.com/articles/no-source",
                                  "sourceId": 99
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Source not found"))
                .andExpect(jsonPath("$.detail").value("Source with id 99 was not found"));
    }

    @Test
    void returnsConflictProblemForDuplicateUrl() throws Exception {
        when(articleService.create(any(CreateArticleRequest.class)))
                .thenThrow(new DuplicateArticleUrlException(
                        "https://example.com/articles/storage-expands"
                ));

        mockMvc.perform(post("/api/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Duplicate article",
                                  "url": "https://example.com/articles/storage-expands",
                                  "sourceId": 7
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Duplicate article URL"))
                .andExpect(jsonPath("$.detail").value(
                        "An article with URL 'https://example.com/articles/storage-expands' already exists"
                ));
    }

    private static ArticleResponse articleResponse() {
        return articleResponse(List.of("battery storage"));
    }

    private static ArticleResponse articleResponse(List<String> tags) {
        return new ArticleResponse(
                1L,
                new ArticleSourceResponse(7L, "Energy Storage News"),
                "https://example.com/articles/storage-expands",
                PUBLISHED_AT,
                COLLECTED_AT,
                new ArticleOriginalResponse(
                        SourceLanguage.EN,
                        "Battery storage expands",
                        "Article summary",
                        "Article content"
                ),
                new ArticleTranslationResponse(
                        TranslationLanguage.ZH_CN,
                        "电池储能扩张",
                        "中文摘要",
                        "中文完整正文"
                ),
                CREATED_AT,
                UPDATED_AT,
                tags
        );
    }

    private static CreateArticleResponse createArticleResponse() {
        return new CreateArticleResponse(
                1L,
                "Battery storage expands",
                "https://example.com/articles/storage-expands",
                "Article summary",
                "Article content",
                PUBLISHED_AT,
                COLLECTED_AT,
                7L,
                "Energy Storage News",
                CREATED_AT,
                UPDATED_AT
        );
    }

    private static ArticlePostProcessingBackfillResponse backfillResponse(
            ArticlePostProcessingBackfillResponse.OverallStatus overallStatus
    ) {
        return new ArticlePostProcessingBackfillResponse(
                41L,
                ArticlePostProcessingBackfillResponse.StepStatus.SUCCESS,
                ArticlePostProcessingBackfillResponse.StepStatus.SUCCESS,
                ArticlePostProcessingBackfillResponse.StepStatus.SUCCESS,
                overallStatus
        );
    }
}
