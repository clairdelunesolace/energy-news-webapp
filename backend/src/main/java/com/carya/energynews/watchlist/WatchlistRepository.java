package com.carya.energynews.watchlist;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    @Override
    @EntityGraph(attributePaths = "keywords")
    List<Watchlist> findAll();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<Watchlist> findAllByEnabledTrueOrderByIdAsc();

    @Query("""
            SELECT DISTINCT watchlist
            FROM Watchlist watchlist
            LEFT JOIN FETCH watchlist.keywords
            WHERE watchlist.id = :id
            """)
    Optional<Watchlist> findWithKeywordsById(@Param("id") Long id);
}
