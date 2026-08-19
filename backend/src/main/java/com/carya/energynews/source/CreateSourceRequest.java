package com.carya.energynews.source;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSourceRequest(
        @NotBlank String name,
        @NotBlank String url,
        @NotNull SourceType type,
        @NotNull SourcePriority priority
) {
}
