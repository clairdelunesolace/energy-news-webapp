package com.carya.energynews.article;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ArticleService articleService;
    private final ArticlePostProcessingBackfillService articlePostProcessingBackfillService;

    public ArticleController(
            ArticleService articleService,
            ArticlePostProcessingBackfillService articlePostProcessingBackfillService
    ) {
        this.articleService = articleService;
        this.articlePostProcessingBackfillService = articlePostProcessingBackfillService;
    }

    @GetMapping
    public ArticlePageResponse getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long keywordId
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidArticlePageException();
        }
        return articleService.getAll(page, size, sourceId, keyword, keywordId);
    }

    @GetMapping("/{id}")
    public ArticleResponse getById(@PathVariable Long id) {
        return articleService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateArticleResponse create(@Valid @RequestBody CreateArticleRequest request) {
        return articleService.create(request);
    }

    @PostMapping("/post-processing/backfill")
    public ResponseEntity<ArticlePostProcessingBackfillResponse> backfillPostProcessing(
            @Valid @RequestBody ArticlePostProcessingBackfillRequest request
    ) {
        ArticlePostProcessingBackfillResponse response = articlePostProcessingBackfillService
                .backfill(request.articleIds().getFirst());
        if (response.overallStatus()
                == ArticlePostProcessingBackfillResponse.OverallStatus.FAILED) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
