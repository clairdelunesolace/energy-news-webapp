package com.carya.energynews.watchlist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWatchlistRequest(
        @NotBlank @Size(max = 100) String name,
        Boolean enabled
) {
}
