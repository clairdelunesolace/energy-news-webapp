package com.carya.energynews.watchlistdiscovery;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record WatchlistDiscoveryRunRequest(
        @NotNull Long watchlistId,
        LocalDate from,
        LocalDate to,
        @Min(1) @Max(20) Integer limitPerKeyword
) {

    public static final int DEFAULT_LIMIT_PER_KEYWORD = 10;

    public WatchlistDiscoveryRunRequest {
        limitPerKeyword = limitPerKeyword == null
                ? DEFAULT_LIMIT_PER_KEYWORD
                : limitPerKeyword;
    }
}
