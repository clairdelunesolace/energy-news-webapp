package com.carya.energynews.dailybriefanalysis;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class DailyBriefAiResultValidator {

    private static final Pattern ENGLISH_UNCERTAINTY = Pattern.compile(
            "\\b(?:reportedly|reported|according\\s+to|sources\\s+say|may|might|could|considering|seeks|plans|planned|expected|proposed|alleged)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CHINESE_UNCERTAINTY = Pattern.compile(
            "据报道|报道称|消息称|可能|或将|(?<![模虚])拟(?![合态])|计划|预计|提议|据称"
    );

    private final boolean evidenceGuardEnabled;

    public DailyBriefAiResultValidator() {
        this(new DailyBriefAiProperties(null, null, true));
    }

    @Autowired
    public DailyBriefAiResultValidator(DailyBriefAiProperties properties) {
        this.evidenceGuardEnabled = properties.evidenceGuardEnabled();
    }

    public DailyBriefAiResult validate(
            DailyBriefAiResult result,
            Collection<DailyBriefAiArticle> snapshotArticles
    ) {
        if (result == null) {
            throw invalid("AI result is missing");
        }
        String headline = requireText(result.headline(), "AI headline must not be blank");
        String overview = requireText(result.overview(), "AI overview must not be blank");
        if (result.events() == null || result.events().isEmpty() || result.events().size() > 5) {
            throw invalid("AI result must contain between 1 and 5 events");
        }

        Set<Long> allowedArticleIds = new HashSet<>();
        Set<Long> uncertainArticleIds = new HashSet<>();
        Map<Long, DailyBriefAiArticle> articlesById = new HashMap<>();
        for (DailyBriefAiArticle article : snapshotArticles) {
            articlesById.put(article.articleId(), article);
            allowedArticleIds.add(article.articleId());
            if (evidenceGuardEnabled
                    && (containsUncertainty(article.title()) || containsUncertainty(article.description()))) {
                uncertainArticleIds.add(article.articleId());
            }
        }
        List<DailyBriefAiEvent> normalizedEvents = new ArrayList<>(result.events().size());
        for (DailyBriefAiEvent event : result.events()) {
            if (event == null) {
                throw invalid("AI event must not be null");
            }
            String title = requireText(event.title(), "AI event title must not be blank");
            String summary = requireText(event.summary(), "AI event summary must not be blank");
            String whyItMatters = requireText(
                    event.whyItMatters(),
                    "AI event whyItMatters must not be blank"
            );
            if (event.supportingArticleIds() == null
                    || event.supportingArticleIds().isEmpty()) {
                throw invalid("AI event supportingArticleIds must not be empty");
            }

            LinkedHashSet<Long> normalizedIds = new LinkedHashSet<>();
            for (Long articleId : event.supportingArticleIds()) {
                if (articleId == null || !allowedArticleIds.contains(articleId)) {
                    throw invalid("AI event references an Article outside the DailyBrief snapshot");
                }
                normalizedIds.add(articleId);
            }
            if (evidenceGuardEnabled && uncertainArticleIds.containsAll(normalizedIds)
                    && !containsUncertainty(title)
                    && !containsUncertainty(summary)) {
                throw invalid("AI event must preserve uncertainty from its supporting Articles");
            }
            if (evidenceGuardEnabled) {
                List<DailyBriefAiArticle> supportingArticles = normalizedIds.stream()
                        .map(articlesById::get).toList();
                DailyBriefAiMonetaryGuard.validate(title, summary, supportingArticles);
            }
            normalizedEvents.add(new DailyBriefAiEvent(
                    title,
                    summary,
                    whyItMatters,
                    List.copyOf(normalizedIds)
            ));
        }
        for (DailyBriefAiEvent event : normalizedEvents) {
            if (evidenceGuardEnabled && uncertainArticleIds.containsAll(event.supportingArticleIds())) {
                Set<String> anchors = DailyBriefAiClaimAnchors.from(event, snapshotArticles, uncertainArticleIds);
                validateGlobalUncertainty(headline, anchors, "headline");
                validateGlobalUncertainty(overview, anchors, "overview");
            }
        }
        return new DailyBriefAiResult(headline, overview, List.copyOf(normalizedEvents));
    }

    private static void validateGlobalUncertainty(String text, Set<String> anchors, String field) {
        // A qualifier in another sentence (or output field) cannot qualify this occurrence.
        for (String sentence : text.split("[。！？!?；;\\r\\n]+|(?<!\\d)\\.|\\.(?!\\d)")) {
            if (anchors.stream().anyMatch(anchor -> DailyBriefAiClaimAnchors.contains(sentence, anchor))
                    && !containsUncertainty(sentence)) {
                throw invalid("AI " + field + " must preserve uncertainty for a referenced event");
            }
        }
    }

    static String withoutUncertainty(String text) {
        return CHINESE_UNCERTAINTY.matcher(ENGLISH_UNCERTAINTY.matcher(text).replaceAll("|")).replaceAll("|");
    }

    private static boolean containsUncertainty(String text) {
        return text != null && (ENGLISH_UNCERTAINTY.matcher(text).find()
                || CHINESE_UNCERTAINTY.matcher(text).find());
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
        return value.strip();
    }

    private static DailyBriefAiValidationException invalid(String message) {
        return new DailyBriefAiValidationException(message);
    }
}
