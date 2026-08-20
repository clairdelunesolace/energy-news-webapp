package com.carya.energynews.filter;

import com.carya.energynews.collection.CollectedArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

@Component
public class KeywordArticleFilter implements ArticleFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeywordArticleFilter.class);

    private final int threshold;
    private final List<Keyword> strongKeywords;
    private final List<Keyword> weakKeywords;

    public KeywordArticleFilter(NewsFilterProperties properties) {
        this.threshold = properties.threshold();
        this.strongKeywords = clean(properties.strongKeywords());
        this.weakKeywords = clean(properties.weakKeywords());
    }

    @Override
    public FilterResult evaluate(CollectedArticle article) {
        FieldMatches titleMatches = findMatches(article.title());
        FieldMatches descriptionMatches = findMatches(article.description());

        int score = titleMatches.strong().size() * 4
                + descriptionMatches.strong().size() * 2
                + titleMatches.weak().size() * 2
                + descriptionMatches.weak().size();
        boolean accepted = score >= threshold;
        String reason = buildReason(score, titleMatches, descriptionMatches);

        LOGGER.debug(
                "Keyword article filter decision for url={}: accepted={}, {}",
                article.url(),
                accepted,
                reason
        );

        return new FilterResult(accepted, reason);
    }

    private FieldMatches findMatches(String value) {
        String normalizedValue = normalize(value);
        List<Keyword> strongMatches = mostSpecificMatches(matchingKeywords(normalizedValue, strongKeywords));
        List<Keyword> weakMatches = mostSpecificMatches(matchingKeywords(normalizedValue, weakKeywords));

        List<String> matchedStrongKeywords = strongMatches.stream()
                .map(Keyword::value)
                .toList();
        List<String> matchedWeakKeywords = weakMatches.stream()
                .filter(weak -> strongMatches.stream()
                        .noneMatch(strong -> strong.normalized().contains(weak.normalized())))
                .map(Keyword::value)
                .toList();

        return new FieldMatches(matchedStrongKeywords, matchedWeakKeywords);
    }

    private List<Keyword> matchingKeywords(String value, List<Keyword> keywords) {
        return keywords.stream()
                .filter(keyword -> value.contains(keyword.normalized()))
                .toList();
    }

    private List<Keyword> mostSpecificMatches(List<Keyword> matches) {
        return matches.stream()
                .filter(candidate -> matches.stream().noneMatch(other ->
                        other.normalized().length() > candidate.normalized().length()
                                && other.normalized().contains(candidate.normalized())
                ))
                .toList();
    }

    private String buildReason(
            int score,
            FieldMatches titleMatches,
            FieldMatches descriptionMatches
    ) {
        StringJoiner reason = new StringJoiner(", ");
        reason.add("score=" + score);
        reason.add("threshold=" + threshold);

        addMatches(reason, "titleStrong", titleMatches.strong());
        addMatches(reason, "titleWeak", titleMatches.weak());
        addMatches(reason, "descriptionStrong", descriptionMatches.strong());
        addMatches(reason, "descriptionWeak", descriptionMatches.weak());

        if (titleMatches.isEmpty() && descriptionMatches.isEmpty()) {
            reason.add("matches=[]");
        }

        return reason.toString();
    }

    private void addMatches(StringJoiner reason, String label, List<String> matches) {
        if (!matches.isEmpty()) {
            reason.add(label + "=" + matches);
        }
    }

    private List<Keyword> clean(List<String> configuredKeywords) {
        Map<String, Keyword> uniqueKeywords = new LinkedHashMap<>();

        for (String configuredKeyword : configuredKeywords) {
            String value = configuredKeyword.trim();
            if (!value.isEmpty()) {
                String normalized = normalize(value);
                uniqueKeywords.putIfAbsent(normalized, new Keyword(value, normalized));
            }
        }

        return List.copyOf(uniqueKeywords.values());
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record Keyword(String value, String normalized) {
    }

    private record FieldMatches(List<String> strong, List<String> weak) {

        private boolean isEmpty() {
            return strong.isEmpty() && weak.isEmpty();
        }
    }
}
