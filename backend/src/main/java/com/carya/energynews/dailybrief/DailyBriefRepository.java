package com.carya.energynews.dailybrief;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyBriefRepository extends JpaRepository<DailyBrief, Long> {

    Optional<DailyBrief> findByWatchlistIdAndBriefDate(Long watchlistId, LocalDate briefDate);
}
