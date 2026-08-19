package com.carya.energynews.article;

import com.carya.energynews.source.SourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class ArticleControllerTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-18T12:00:00Z");
    private static final Instant COLLECTED_AT = Instant.parse("2026-08-19T06:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-08-19T06:00:01Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-19T06:00:01Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleService articleService;

    @Test
    void returnsAllArticles() throws Exception {
        when(articleService.getAll()).thenReturn(List.of(articleResponse()));

        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Battery storage expands"))
                .andExpect(jsonPath("$[0].sourceId").value(7))
                .andExpect(jsonPath("$[0].sourceName").value("Energy Storage News"))
                .andExpect(jsonPath("$[0].source").doesNotExist());
    }

    @Test
    void returnsArticleById() throws Exception {
        when(articleService.getById(1L)).thenReturn(articleResponse());

        mockMvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.url").value("https://example.com/articles/storage-expands"))
                .andExpect(jsonPath("$.collectedAt").value("2026-08-19T06:00:00Z"));
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
        when(articleService.create(request)).thenReturn(articleResponse());

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
        return new ArticleResponse(
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
}
