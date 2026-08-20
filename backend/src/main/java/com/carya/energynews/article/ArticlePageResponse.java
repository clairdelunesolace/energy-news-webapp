package com.carya.energynews.article;

import java.util.List;

public record ArticlePageResponse(
        List<ArticleResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
