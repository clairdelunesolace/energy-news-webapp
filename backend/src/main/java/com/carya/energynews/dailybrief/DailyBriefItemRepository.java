package com.carya.energynews.dailybrief;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DailyBriefItemRepository extends JpaRepository<DailyBriefItem, Long> {

    @EntityGraph(attributePaths = {"article", "article.source"})
    List<DailyBriefItem> findAllByDailyBriefIdOrderByRankAsc(Long dailyBriefId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM DailyBriefItem item WHERE item.dailyBrief.id = :dailyBriefId")
    int deleteAllByDailyBriefId(@Param("dailyBriefId") Long dailyBriefId);

    long countByDailyBriefId(Long dailyBriefId);
}
