package com.carya.energynews.translation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArticleTranslationRepository extends JpaRepository<ArticleTranslation, Long> {

    Optional<ArticleTranslation> findByArticleIdAndLanguage(
            Long articleId,
            TranslationLanguage language
    );
}
