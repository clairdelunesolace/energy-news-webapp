package com.carya.energynews.discovery;

import java.util.List;

public record NewsDiscoveryPreviewResponse(
        String provider,
        String keyword,
        int count,
        List<DiscoveredArticle> results
) {
}
