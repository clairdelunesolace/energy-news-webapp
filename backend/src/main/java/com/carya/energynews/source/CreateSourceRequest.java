package com.carya.energynews.source;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSourceRequest(
        @NotBlank String name,
        @NotBlank String url,
        @NotNull SourceType type,
        @NotNull SourcePriority priority,
        SourceLanguage language
) {

    public CreateSourceRequest(
            String name,
            String url,
            SourceType type,
            SourcePriority priority
    ) {
        this(name, url, type, priority, null);
    }
}
