package com.carya.energynews.article;

import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.translation.TranslationLanguage;
import com.carya.energynews.translation.TranslationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    boolean existsByUrl(String url);

    @EntityGraph(attributePaths = "source")
    Optional<Article> findByUrl(String url);

    @Query(
            value = """
                    SELECT article
                    FROM Article article
                    JOIN FETCH article.source
                    WHERE (:sourceId IS NULL OR article.source.id = :sourceId)
                      AND (
                          LOWER(article.title) LIKE CONCAT('%', :keyword, '%')
                          OR LOWER(article.description) LIKE CONCAT('%', :keyword, '%')
                      )
                    ORDER BY
                        CASE WHEN article.publishedAt IS NULL THEN 1 ELSE 0 END,
                        article.publishedAt DESC,
                        article.collectedAt DESC,
                        article.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(article)
                    FROM Article article
                    WHERE (:sourceId IS NULL OR article.source.id = :sourceId)
                      AND (
                          LOWER(article.title) LIKE CONCAT('%', :keyword, '%')
                          OR LOWER(article.description) LIKE CONCAT('%', :keyword, '%')
                      )
                    """
    )
    Page<Article> findAllFiltered(
            @Param("sourceId") Long sourceId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT article
            FROM Article article
            JOIN FETCH article.source source
            WHERE source.language = :sourceLanguage
              AND NOT EXISTS (
                  SELECT translation.id
                  FROM ArticleTranslation translation
                  WHERE translation.article = article
                    AND translation.language = :targetLanguage
                    AND translation.status = :successfulStatus
              )
            ORDER BY
                CASE WHEN article.publishedAt IS NULL THEN 1 ELSE 0 END,
                article.publishedAt DESC,
                article.collectedAt DESC,
                article.id DESC
            """)
    List<Article> findTranslationBackfillCandidates(
            @Param("sourceLanguage") SourceLanguage sourceLanguage,
            @Param("targetLanguage") TranslationLanguage targetLanguage,
            @Param("successfulStatus") TranslationStatus successfulStatus,
            Pageable pageable
    );

    @Query("""
            SELECT article
            FROM Article article
            WHERE article.content IS NULL OR TRIM(article.content) = ''
            ORDER BY
                CASE WHEN article.publishedAt IS NULL THEN 1 ELSE 0 END,
                article.publishedAt DESC,
                article.collectedAt DESC,
                article.id DESC
            """)
    List<Article> findContentBackfillCandidates(Pageable pageable);
}
