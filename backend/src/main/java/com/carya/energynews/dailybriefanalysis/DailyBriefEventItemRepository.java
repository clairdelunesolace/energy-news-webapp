package com.carya.energynews.dailybriefanalysis;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyBriefEventItemRepository extends JpaRepository<DailyBriefEventItem, Long> {

    @EntityGraph(attributePaths = {"event", "dailyBriefItem", "dailyBriefItem.article"})
    List<DailyBriefEventItem> findAllByEventIdInOrderByEventIdAscSupportRankAsc(
            List<Long> eventIds
    );
}
