package com.carya.energynews.watchlist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKeywordRequest(
        @NotBlank @Size(max = 200) String keyword,
        Boolean enabled
) {
}
