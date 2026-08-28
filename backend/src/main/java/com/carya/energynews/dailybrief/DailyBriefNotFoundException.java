package com.carya.energynews.dailybrief;

import java.time.LocalDate;

public class DailyBriefNotFoundException extends RuntimeException {

    public DailyBriefNotFoundException(Long id) {
        super("Daily brief " + id + " was not found");
    }

    public DailyBriefNotFoundException(Long watchlistId, LocalDate date) {
        super("Daily brief for watchlist " + watchlistId + " on " + date + " was not found");
    }
}
