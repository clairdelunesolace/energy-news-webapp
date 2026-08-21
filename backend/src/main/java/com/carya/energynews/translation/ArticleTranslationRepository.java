package com.carya.energynews.translation;

import com.carya.energynews.source.SourceLanguage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    @Query("""
            SELECT translation
            FROM ArticleTranslation translation
            JOIN FETCH translation.article article
            JOIN FETCH article.source source
            WHERE source.language = :sourceLanguage
              AND article.content IS NOT NULL
              AND TRIM(article.content) <> ''
              AND translation.language = :targetLanguage
              AND translation.status = :successfulStatus
              AND (
                  translation.contentStatus IS NULL
                  OR translation.contentStatus IN :retryStatuses
              )
            ORDER BY
                CASE WHEN article.publishedAt IS NULL THEN 1 ELSE 0 END,
                article.publishedAt DESC,
                article.collectedAt DESC,
                article.id DESC
            """)
    List<ArticleTranslation> findContentTranslationBackfillCandidates(
            @Param("sourceLanguage") SourceLanguage sourceLanguage,
            @Param("targetLanguage") TranslationLanguage targetLanguage,
            @Param("successfulStatus") TranslationStatus successfulStatus,
            @Param("retryStatuses") Collection<ContentTranslationStatus> retryStatuses,
            Pageable pageable
    );
}
