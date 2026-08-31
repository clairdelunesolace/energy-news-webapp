package com.carya.energynews.dailybriefanalysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DailyBriefEventRepository extends JpaRepository<DailyBriefEvent, Long> {

    List<DailyBriefEvent> findAllByAnalysisIdOrderByEventRankAsc(Long analysisId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM DailyBriefEvent event WHERE event.analysis.id = :analysisId")
    int deleteAllByAnalysisId(@Param("analysisId") Long analysisId);
}
