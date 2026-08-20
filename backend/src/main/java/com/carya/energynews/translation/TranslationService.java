package com.carya.energynews.translation;

import com.carya.energynews.article.Article;
import com.carya.energynews.source.SourceLanguage;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TranslationService {

    private final ArticleTranslationRepository articleTranslationRepository;
    private final TranslationProvider translationProvider;

    public TranslationService(
            ArticleTranslationRepository articleTranslationRepository,
            TranslationProvider translationProvider
    ) {
        this.articleTranslationRepository = articleTranslationRepository;
        this.translationProvider = translationProvider;
    }

    public ArticleTranslation translate(Article article, TranslationLanguage targetLanguage) {
        SourceLanguage sourceLanguage = validateRequest(article, targetLanguage);

        ArticleTranslation translation = articleTranslationRepository
                .findByArticleIdAndLanguage(article.getId(), targetLanguage)
                .orElseGet(() -> new ArticleTranslation(article, targetLanguage));

        if (translation.getStatus() == TranslationStatus.SUCCESS) {
            return translation;
        }

        preparePending(translation);
        translation = articleTranslationRepository.saveAndFlush(translation);

        TranslationInput input = new TranslationInput(
                sourceLanguage,
                targetLanguage,
                article.getTitle(),
                article.getDescription()
        );

        TranslationOutput output;
        try {
            output = translationProvider.translate(input);
        } catch (TranslationException exception) {
            markFailed(translation);
            articleTranslationRepository.saveAndFlush(translation);
            throw exception;
        }

        translation.setTitle(output.translatedTitle());
        translation.setDescription(output.translatedDescription());
        translation.setStatus(TranslationStatus.SUCCESS);
        translation.setTranslatedAt(Instant.now());
        return articleTranslationRepository.saveAndFlush(translation);
    }

    private SourceLanguage validateRequest(Article article, TranslationLanguage targetLanguage) {
        if (article == null || article.getId() == null) {
            throw new TranslationException("A persisted Article is required for translation");
        }
        if (targetLanguage == null) {
            throw new TranslationException("Translation target language is required");
        }
        if (article.getSource() == null || article.getSource().getLanguage() == null) {
            throw new TranslationException("Article source language is required for translation");
        }

        SourceLanguage sourceLanguage = article.getSource().getLanguage();
        if (sourceLanguage == SourceLanguage.ZH_CN
                && targetLanguage == TranslationLanguage.ZH_CN) {
            throw new TranslationException(
                    "Article source is already ZH_CN; translation to ZH_CN is not required"
            );
        }
        if (sourceLanguage != SourceLanguage.EN
                || targetLanguage != TranslationLanguage.ZH_CN) {
            throw new TranslationException(
                    "Unsupported translation from " + sourceLanguage + " to " + targetLanguage
            );
        }
        return sourceLanguage;
    }

    private void preparePending(ArticleTranslation translation) {
        translation.setTitle(null);
        translation.setDescription(null);
        translation.setStatus(TranslationStatus.PENDING);
        translation.setTranslatedAt(null);
    }

    private void markFailed(ArticleTranslation translation) {
        translation.setTitle(null);
        translation.setDescription(null);
        translation.setStatus(TranslationStatus.FAILED);
        translation.setTranslatedAt(null);
    }
}
