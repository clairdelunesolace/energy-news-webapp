package com.carya.energynews.content;

public record ArticleContentBackfillResult(
        int selected,
        int fetched,
        int failed
) {
}
