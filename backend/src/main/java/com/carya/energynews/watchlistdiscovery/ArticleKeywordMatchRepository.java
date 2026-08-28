package com.carya.energynews.watchlistdiscovery;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleKeywordMatchRepository extends JpaRepository<ArticleKeywordMatch, Long> {

    boolean existsByArticleIdAndKeywordId(Long articleId, Long keywordId);

    long countByArticleId(Long articleId);

    long countByKeywordId(Long keywordId);
}
