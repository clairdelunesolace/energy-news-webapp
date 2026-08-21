package com.carya.energynews.translation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/translations")
public class ArticleContentTranslationBackfillController {

    private static final String DEFAULT_LIMIT = "1";

    private final ArticleContentTranslationBackfillService backfillService;

    public ArticleContentTranslationBackfillController(
            ArticleContentTranslationBackfillService backfillService
    ) {
        this.backfillService = backfillService;
    }

    // Development maintenance endpoint; secure or restrict it before public production deployment.
    @PostMapping("/content-backfill")
    public ArticleContentTranslationBackfillResult backfill(
            @RequestParam(defaultValue = DEFAULT_LIMIT) int limit
    ) {
        if (limit < ArticleContentTranslationBackfillService.MIN_LIMIT
                || limit > ArticleContentTranslationBackfillService.MAX_LIMIT) {
            throw new InvalidArticleContentTranslationBackfillLimitException();
        }
        return backfillService.backfill(limit);
    }
}
