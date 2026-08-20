package com.carya.energynews.translation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArticleTranslationRepository extends JpaRepository<ArticleTranslation, Long> {

    Optional<ArticleTranslation> findByArticleIdAndLanguage(
            Long articleId,
            TranslationLanguage language
    );

    Optional<ArticleTranslation> findByArticleIdAndLanguageAndStatus(
            Long articleId,
            TranslationLanguage language,
            TranslationStatus status
    );

    List<ArticleTranslation> findAllByArticleIdInAndLanguageAndStatus(
            List<Long> articleIds,
            TranslationLanguage language,
            TranslationStatus status
    );
}
