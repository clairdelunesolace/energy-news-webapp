package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.discovery.DiscoveredArticle;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class DiscoveryKeywordMatcher {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public boolean matches(String keyword, DiscoveredArticle article) {
        String normalizedKeyword = normalize(keyword);
        return matchesText(normalizedKeyword, article.title())
                || matchesText(normalizedKeyword, article.description());
    }

    private boolean matchesText(String normalizedKeyword, String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalizedText = normalize(text);
        if (normalizedKeyword.contains(" ")) {
            return normalizedText.contains(normalizedKeyword);
        }

        Pattern wholeToken = Pattern.compile(
                "(?<![\\p{L}\\p{N}_])" + Pattern.quote(normalizedKeyword)
                        + "(?![\\p{L}\\p{N}_])"
        );
        return wholeToken.matcher(normalizedText).find();
    }

    private String normalize(String value) {
        return WHITESPACE.matcher(value.strip().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }
}
