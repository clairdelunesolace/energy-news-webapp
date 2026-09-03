package com.carya.energynews.watchlist;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Component
public class WatchlistKeywordMatcher {

    public List<String> matchTags(String title, String description, List<String> keywords) {
        String normalizedTitle = normalize(title);
        String normalizedDescription = normalize(description);
        Map<String, String> tags = new TreeMap<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            String normalizedKeyword = normalize(keyword.trim());
            if (normalizedTitle.contains(normalizedKeyword)
                    || normalizedDescription.contains(normalizedKeyword)) {
                // Keep an exact configured label, with a stable tie-break across Watchlists.
                tags.merge(normalizedKeyword, keyword,
                        (left, right) -> left.compareTo(right) <= 0 ? left : right);
            }
        }
        return List.copyOf(tags.values());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
