package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.dailybrief.DailyBriefCandidate;
import com.carya.energynews.dailybrief.DailyBriefMatchedKeyword;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ArticleKeywordMatchRepository extends JpaRepository<ArticleKeywordMatch, Long> {

    boolean existsByArticleIdAndKeywordId(Long articleId, Long keywordId);

    long countByArticleId(Long articleId);

    long countByKeywordId(Long keywordId);

    @Query(
            value = """
                    SELECT new com.carya.energynews.dailybrief.DailyBriefCandidate(
                        article.id,
                        COUNT(DISTINCT keyword.id),
                        COALESCE(article.publishedAt, article.collectedAt)
                    )
                    FROM ArticleKeywordMatch match
                    JOIN match.article article
                    JOIN match.keyword keyword
                    WHERE keyword.watchlist.id = :watchlistId
                      AND COALESCE(article.publishedAt, article.collectedAt) >= :windowStart
                      AND COALESCE(article.publishedAt, article.collectedAt) < :windowEnd
                    GROUP BY article.id, article.publishedAt, article.collectedAt
                    ORDER BY
                        COUNT(DISTINCT keyword.id) DESC,
                        COALESCE(article.publishedAt, article.collectedAt) DESC,
                        article.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT article.id)
                    FROM ArticleKeywordMatch match
                    JOIN match.article article
                    JOIN match.keyword keyword
                    WHERE keyword.watchlist.id = :watchlistId
                      AND COALESCE(article.publishedAt, article.collectedAt) >= :windowStart
                      AND COALESCE(article.publishedAt, article.collectedAt) < :windowEnd
                    """
    )
    Page<DailyBriefCandidate> findDailyBriefCandidates(
            @Param("watchlistId") Long watchlistId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd,
            Pageable pageable
    );

    @Query("""
            SELECT new com.carya.energynews.dailybrief.DailyBriefMatchedKeyword(
                article.id,
                keyword.id,
                keyword.keyword
            )
            FROM ArticleKeywordMatch match
            JOIN match.article article
            JOIN match.keyword keyword
            WHERE keyword.watchlist.id = :watchlistId
              AND article.id IN :articleIds
            ORDER BY article.id ASC, LOWER(keyword.keyword) ASC, keyword.id ASC
            """)
    List<DailyBriefMatchedKeyword> findDailyBriefMatchedKeywords(
            @Param("watchlistId") Long watchlistId,
            @Param("articleIds") List<Long> articleIds
    );
}
