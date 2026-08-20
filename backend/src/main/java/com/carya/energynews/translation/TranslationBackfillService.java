package com.carya.energynews.translation;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleRepository;
import com.carya.energynews.source.SourceLanguage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TranslationBackfillService {

    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 100;

    private final ArticleRepository articleRepository;
    private final TranslationService translationService;

    public TranslationBackfillService(
            ArticleRepository articleRepository,
            TranslationService translationService
    ) {
        this.articleRepository = articleRepository;
        this.translationService = translationService;
    }

    public TranslationBackfillResult backfill(int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new InvalidTranslationBackfillLimitException();
        }

        List<Article> candidates = articleRepository.findTranslationBackfillCandidates(
                SourceLanguage.EN,
                TranslationLanguage.ZH_CN,
                TranslationStatus.SUCCESS,
                PageRequest.of(0, limit)
        );

        int translated = 0;
        int failed = 0;
        for (Article article : candidates) {
            try {
                translationService.translate(article, TranslationLanguage.ZH_CN);
                translated++;
            } catch (TranslationException exception) {
                failed++;
            }
        }

        return new TranslationBackfillResult(candidates.size(), translated, failed);
    }
}
