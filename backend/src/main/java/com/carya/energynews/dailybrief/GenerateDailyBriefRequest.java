package com.carya.energynews.dailybrief;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record GenerateDailyBriefRequest(
        @NotNull Long watchlistId,
        LocalDate date,
        @Min(1) @Max(20) Integer maxItems
) {
}
