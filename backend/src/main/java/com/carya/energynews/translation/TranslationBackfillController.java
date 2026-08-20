package com.carya.energynews.translation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/translations")
public class TranslationBackfillController {

    private static final String DEFAULT_LIMIT = "20";

    private final TranslationBackfillService translationBackfillService;

    public TranslationBackfillController(TranslationBackfillService translationBackfillService) {
        this.translationBackfillService = translationBackfillService;
    }

    // Development maintenance endpoint; secure or restrict it before public production deployment.
    @PostMapping("/backfill")
    public TranslationBackfillResult backfill(
            @RequestParam(defaultValue = DEFAULT_LIMIT) int limit
    ) {
        if (limit < TranslationBackfillService.MIN_LIMIT
                || limit > TranslationBackfillService.MAX_LIMIT) {
            throw new InvalidTranslationBackfillLimitException();
        }
        return translationBackfillService.backfill(limit);
    }
}
