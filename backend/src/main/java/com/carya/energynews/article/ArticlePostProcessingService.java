package com.carya.energynews.article;

import com.carya.energynews.content.ArticleContentFetchException;
import com.carya.energynews.content.ArticleContentService;
import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.translation.ArticleContentTranslationService;
import com.carya.energynews.translation.ContentTranslationStatus;
import com.carya.energynews.translation.TranslationException;
import com.carya.energynews.translation.TranslationLanguage;
import com.carya.energynews.translation.TranslationService;
import com.carya.energynews.translation.TranslationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Post-processes an Article after its caller has explicitly authorized content extraction.
 * Automated callers remain responsible for applying source-level enrichment policy first.
 */
@Service
public class ArticlePostProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ArticlePostProcessingService.class
    );

    private final TranslationService translationService;
    private final ArticleContentService articleContentService;
    private final ArticleContentTranslationService articleContentTranslationService;

    public ArticlePostProcessingService(
            TranslationService translationService,
            ArticleContentService articleContentService,
            ArticleContentTranslationService articleContentTranslationService
    ) {
        this.translationService = translationService;
        this.articleContentService = articleContentService;
        this.articleContentTranslationService = articleContentTranslationService;
    }

    public ArticlePostProcessingResult process(Article article) {
        boolean metadataTranslationSucceeded = false;
        boolean metadataTranslationFailed = false;
        boolean contentExtractionSucceeded = false;
        boolean contentExtractionFailed = false;
        boolean contentTranslationSucceeded = false;
        boolean contentTranslationFailed = false;

        boolean englishArticle = article.getSource().getLanguage() == SourceLanguage.EN;
        if (englishArticle) {
            try {
                metadataTranslationSucceeded = translationService.translate(
                        article,
                        TranslationLanguage.ZH_CN
                ).getStatus() == TranslationStatus.SUCCESS;
                metadataTranslationFailed = !metadataTranslationSucceeded;
            } catch (TranslationException exception) {
                metadataTranslationFailed = true;
                logFailure("metadata translation", article, exception);
            }
        }

        try {
            article = articleContentService.enrichContent(article);
            contentExtractionSucceeded = hasContent(article);
            contentExtractionFailed = !contentExtractionSucceeded;
        } catch (ArticleContentFetchException exception) {
            contentExtractionFailed = true;
            logFailure("content extraction", article, exception);
        }

        if (englishArticle && metadataTranslationSucceeded && contentExtractionSucceeded) {
            try {
                contentTranslationSucceeded = articleContentTranslationService.translateContent(
                        article,
                        TranslationLanguage.ZH_CN
                ).getContentStatus() == ContentTranslationStatus.SUCCESS;
                contentTranslationFailed = !contentTranslationSucceeded;
            } catch (TranslationException exception) {
                contentTranslationFailed = true;
                logFailure("content translation", article, exception);
            }
        }

        return new ArticlePostProcessingResult(
                metadataTranslationSucceeded,
                metadataTranslationFailed,
                contentExtractionSucceeded,
                contentExtractionFailed,
                contentTranslationSucceeded,
                contentTranslationFailed
        );
    }

    private boolean hasContent(Article article) {
        return article != null
                && article.getContent() != null
                && !article.getContent().isBlank();
    }

    private void logFailure(String stage, Article article, RuntimeException exception) {
        LOGGER.warn(
                "Article {} failed: articleId={}, source={}, url={}, reason={}",
                stage,
                article.getId(),
                article.getSource().getName(),
                article.getUrl(),
                exception.getMessage()
        );
    }
}
