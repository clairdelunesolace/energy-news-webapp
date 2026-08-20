package com.carya.energynews.article;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
                          :keyword IS NULL
                          OR LOWER(article.title) LIKE CONCAT('%', :keyword, '%')
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
                          :keyword IS NULL
                          OR LOWER(article.title) LIKE CONCAT('%', :keyword, '%')
                          OR LOWER(article.description) LIKE CONCAT('%', :keyword, '%')
                      )
                    """
    )
    Page<Article> findAllFiltered(
            @Param("sourceId") Long sourceId,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
