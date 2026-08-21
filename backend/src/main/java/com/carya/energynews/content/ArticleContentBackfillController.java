package com.carya.energynews.content;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/articles")
public class ArticleContentBackfillController {

    private static final String DEFAULT_LIMIT = "5";

    private final ArticleContentBackfillService articleContentBackfillService;

    public ArticleContentBackfillController(
            ArticleContentBackfillService articleContentBackfillService
    ) {
        this.articleContentBackfillService = articleContentBackfillService;
    }

    // Maintenance endpoint: secure or restrict it before public production deployment.
    @PostMapping("/content-backfill")
    public ArticleContentBackfillResult backfill(
            @RequestParam(defaultValue = DEFAULT_LIMIT) int limit
    ) {
        if (limit < ArticleContentBackfillService.MIN_LIMIT
                || limit > ArticleContentBackfillService.MAX_LIMIT) {
            throw new InvalidArticleContentBackfillLimitException();
        }
        return articleContentBackfillService.backfill(limit);
    }
}
