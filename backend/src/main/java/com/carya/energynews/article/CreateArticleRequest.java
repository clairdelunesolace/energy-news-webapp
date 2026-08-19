package com.carya.energynews.article;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateArticleRequest(
        @NotBlank String title,
        @NotBlank String url,
        String description,
        String content,
        Instant publishedAt,
        @NotNull Long sourceId
) {
}
