package com.carya.energynews.watchlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    @Query("SELECT keyword.keyword FROM Keyword keyword WHERE keyword.enabled = true")
    List<String> findEnabledKeywordTexts();

    boolean existsByWatchlistIdAndKeywordIgnoreCase(Long watchlistId, String keyword);

    boolean existsByWatchlistIdAndKeywordIgnoreCaseAndIdNot(
            Long watchlistId,
            String keyword,
            Long id
    );
}
