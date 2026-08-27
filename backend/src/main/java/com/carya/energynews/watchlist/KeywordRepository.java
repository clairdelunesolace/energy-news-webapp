package com.carya.energynews.watchlist;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    boolean existsByWatchlistIdAndKeywordIgnoreCase(Long watchlistId, String keyword);

    boolean existsByWatchlistIdAndKeywordIgnoreCaseAndIdNot(
            Long watchlistId,
            String keyword,
            Long id
    );
}
