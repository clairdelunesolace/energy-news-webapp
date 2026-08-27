package com.carya.energynews.watchlist;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateKeywordRequest(
        @Pattern(
                regexp = ".*\\S.*",
                flags = Pattern.Flag.DOTALL,
                message = "must not be blank"
        )
        @Size(max = 200)
        String keyword,
        Boolean enabled
) {
}
