package com.carya.energynews.dailybrief;

public class DailyBriefWatchlistDisabledException extends RuntimeException {

    public DailyBriefWatchlistDisabledException(Long watchlistId) {
        super("Watchlist " + watchlistId + " is disabled");
    }
}
