package com.carya.energynews.translation;

import com.carya.energynews.article.Article;
import com.carya.energynews.source.SourceLanguage;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ArticleContentTranslationService {

    private final ArticleTranslationRepository articleTranslationRepository;
    private final TranslationProvider translationProvider;

    public ArticleContentTranslationService(
            ArticleTranslationRepository articleTranslationRepository,
            TranslationProvider translationProvider
    ) {
        this.articleTranslationRepository = articleTranslationRepository;
        this.translationProvider = translationProvider;
    }

    public ArticleTranslation translateContent(
            Article article,
            TranslationLanguage targetLanguage
    ) {
        SourceLanguage sourceLanguage = validateArticle(article, targetLanguage);
        ArticleTranslation translation = articleTranslationRepository
                .findByArticleIdAndLanguage(article.getId(), targetLanguage)
                .orElseThrow(() -> new TranslationException(
                        "A successful title and description translation is required"
                ));

        if (translation.getStatus() != TranslationStatus.SUCCESS) {
            throw new TranslationException(
                    "Title and description translation must be SUCCESS before translating content"
            );
        }
        if (translation.getContentStatus() == ContentTranslationStatus.SUCCESS
                && translation.getContent() != null
                && !translation.getContent().isBlank()) {
            return translation;
        }

        preparePending(translation);
        translation = articleTranslationRepository.saveAndFlush(translation);

        TranslationOutput output;
        try {
            output = translationProvider.translate(new TranslationInput(
                    sourceLanguage,
                    targetLanguage,
                    null,
                    null,
                    article.getContent()
            ));
            if (output == null
                    || output.translatedContent() == null
                    || output.translatedContent().isBlank()) {
                throw new TranslationException(
                        "Translation provider returned no translated content"
                );
            }
        } catch (TranslationException exception) {
            markFailed(translation);
            articleTranslationRepository.saveAndFlush(translation);
            throw exception;
        }

        translation.setContent(output.translatedContent());
        translation.setContentStatus(ContentTranslationStatus.SUCCESS);
        translation.setContentTranslatedAt(Instant.now());
        return articleTranslationRepository.saveAndFlush(translation);
    }

    private SourceLanguage validateArticle(
            Article article,
            TranslationLanguage targetLanguage
    ) {
        if (article == null || article.getId() == null) {
            throw new TranslationException(
                    "A persisted Article is required for content translation"
            );
        }
        if (article.getContent() == null || article.getContent().isBlank()) {
            throw new TranslationException("Article content is required for translation");
        }
        if (targetLanguage == null) {
            throw new TranslationException("Translation target language is required");
        }
        if (article.getSource() == null || article.getSource().getLanguage() == null) {
            throw new TranslationException("Article source language is required for translation");
        }

        SourceLanguage sourceLanguage = article.getSource().getLanguage();
        if (sourceLanguage != SourceLanguage.EN
                || targetLanguage != TranslationLanguage.ZH_CN) {
            throw new TranslationException(
                    "Unsupported content translation from "
                            + sourceLanguage + " to " + targetLanguage
            );
        }
        return sourceLanguage;
    }

    private void preparePending(ArticleTranslation translation) {
        translation.setContent(null);
        translation.setContentStatus(ContentTranslationStatus.PENDING);
        translation.setContentTranslatedAt(null);
    }

    private void markFailed(ArticleTranslation translation) {
        translation.setContent(null);
        translation.setContentStatus(ContentTranslationStatus.FAILED);
        translation.setContentTranslatedAt(null);
    }
}
