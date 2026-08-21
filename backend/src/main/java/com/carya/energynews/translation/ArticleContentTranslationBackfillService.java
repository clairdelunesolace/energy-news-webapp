package com.carya.energynews.translation;

import com.carya.energynews.source.SourceLanguage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ArticleContentTranslationBackfillService {

    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 10;

    private static final List<ContentTranslationStatus> RETRY_STATUSES = List.of(
            ContentTranslationStatus.PENDING,
            ContentTranslationStatus.FAILED
    );

    private final ArticleTranslationRepository articleTranslationRepository;
    private final ArticleContentTranslationService articleContentTranslationService;

    public ArticleContentTranslationBackfillService(
            ArticleTranslationRepository articleTranslationRepository,
            ArticleContentTranslationService articleContentTranslationService
    ) {
        this.articleTranslationRepository = articleTranslationRepository;
        this.articleContentTranslationService = articleContentTranslationService;
    }

    public ArticleContentTranslationBackfillResult backfill(int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new InvalidArticleContentTranslationBackfillLimitException();
        }

        List<ArticleTranslation> candidates = articleTranslationRepository
                .findContentTranslationBackfillCandidates(
                        SourceLanguage.EN,
                        TranslationLanguage.ZH_CN,
                        TranslationStatus.SUCCESS,
                        RETRY_STATUSES,
                        PageRequest.of(0, limit)
                );

        int translated = 0;
        int failed = 0;
        for (ArticleTranslation candidate : candidates) {
            try {
                articleContentTranslationService.translateContent(
                        candidate.getArticle(),
                        TranslationLanguage.ZH_CN
                );
                translated++;
            } catch (TranslationException exception) {
                failed++;
            }
        }

        return new ArticleContentTranslationBackfillResult(
                candidates.size(),
                translated,
                failed
        );
    }
}
