package com.carya.energynews.article;

import java.util.Objects;

public record ArticleIngestionResult(
        Status status,
        Article article
) {

    public ArticleIngestionResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(article, "article must not be null");
    }

    public static ArticleIngestionResult saved(Article article) {
        return new ArticleIngestionResult(Status.SAVED, article);
    }

    public static ArticleIngestionResult duplicate(Article article) {
        return new ArticleIngestionResult(Status.DUPLICATE, article);
    }

    public enum Status {
        SAVED,
        DUPLICATE
    }
}
