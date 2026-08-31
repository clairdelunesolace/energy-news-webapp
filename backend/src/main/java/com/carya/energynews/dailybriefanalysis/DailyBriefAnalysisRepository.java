package com.carya.energynews.dailybriefanalysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DailyBriefAnalysisRepository extends JpaRepository<DailyBriefAnalysis, Long> {

    Optional<DailyBriefAnalysis> findByDailyBriefId(Long dailyBriefId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM DailyBriefAnalysis analysis WHERE analysis.dailyBrief.id = :dailyBriefId")
    int deleteAllByDailyBriefId(@Param("dailyBriefId") Long dailyBriefId);
}
