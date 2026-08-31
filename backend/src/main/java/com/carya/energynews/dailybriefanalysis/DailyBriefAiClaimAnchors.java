package com.carya.energynews.dailybriefanalysis;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Conservative lexical links, not entity resolution or general semantic fact checking. */
final class DailyBriefAiClaimAnchors {

    private static final Pattern NAMED_PHRASE = Pattern.compile(
            "(?<![\\p{IsLatin}\\d])\\p{Lu}[\\p{IsLatin}\\d'’-]*"
                    + "(?:\\s+\\p{Lu}[\\p{IsLatin}\\d'’-]*)+(?![\\p{IsLatin}\\d])"
    );
    private static final Pattern QUOTED_PHRASE = Pattern.compile(
            "[\"“「『《]([^\"”」』》\\r\\n]{3,80})[\"”」』》]"
    );
    private static final Pattern TOKEN = Pattern.compile("[\\p{IsLatin}\\d]+(?:['’-][\\p{IsLatin}\\d]+)*|\\p{IsHan}");
    private static final Set<String> FUNCTION_WORDS = Set.of(
            "a", "an", "the", "of", "to", "for", "in", "on", "at", "by", "with", "from",
            "and", "or", "as", "is", "are", "was", "were", "be", "been", "has", "have", "had", "its", "it"
    );

    private DailyBriefAiClaimAnchors() {
    }

    static Set<String> from(
            DailyBriefAiEvent event, Collection<DailyBriefAiArticle> snapshot, Set<Long> uncertainArticleIds
    ) {
        Set<String> candidates = new HashSet<>();
        String eventText = DailyBriefAiResultValidator.withoutUncertainty(event.title() + "\n" + event.summary());
        var names = NAMED_PHRASE.matcher(eventText);
        while (names.find()) {
            String name = normalize(names.group());
            if (Stream.of(name.split(" ")).noneMatch(FUNCTION_WORDS::contains)) {
                candidates.add(name);
            }
        }
        var quotes = QUOTED_PHRASE.matcher(eventText);
        while (quotes.find()) {
            candidates.add(normalize(quotes.group(1)));
        }
        // Shared title phrases can link non-Latin entities and claims without a name dictionary.
        // Never make a single company token, keyword, number, or uncertainty cue an anchor.
        for (String part : DailyBriefAiResultValidator.withoutUncertainty(event.title())
                .split("[^\\p{L}\\p{N}\\s'’-]+")) {
            addTitlePhrases(part, candidates);
        }

        List<String> supporting = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (DailyBriefAiArticle article : snapshot) {
            boolean cited = event.supportingArticleIds().contains(article.articleId());
            if (!cited && uncertainArticleIds.contains(article.articleId())) {
                continue;
            }
            List<String> destination = cited ? supporting : other;
            destination.add(normalize(article.title()));
            destination.add(normalize(article.description()));
        }
        // Terms shared with confirmed stories are ambiguous context, not distinctive event links.
        // Additional uncertain coverage must not erase an anchor from the cited evidence.
        candidates.removeIf(anchor -> anchor.isBlank()
                || supporting.stream().noneMatch(text -> contains(text, anchor))
                || other.stream().anyMatch(text -> contains(text, anchor)));
        return candidates;
    }

    private static void addTitlePhrases(String title, Set<String> candidates) {
        List<String> tokens = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        var matcher = TOKEN.matcher(title);
        while (matcher.find()) {
            tokens.add(matcher.group());
            starts.add(matcher.start());
            ends.add(matcher.end());
        }
        for (int start = 0; start < tokens.size(); start++) {
            int han = 0;
            int words = 0;
            for (int end = start; end < Math.min(tokens.size(), start + 12); end++) {
                String token = tokens.get(end);
                if (Character.UnicodeScript.of(token.codePointAt(0)) == Character.UnicodeScript.HAN) {
                    han++;
                } else if (token.length() >= 3 && !FUNCTION_WORDS.contains(token.toLowerCase(Locale.ROOT))
                        && Character.isLetter(token.charAt(0))) {
                    words++;
                }
                if (han >= 6 || words >= 3 || (han >= 2 && words >= 1)) {
                    candidates.add(normalize(title.substring(starts.get(start), ends.get(end))));
                }
            }
        }
    }

    static boolean contains(String text, String anchor) {
        String normalized = normalize(text);
        // Latin names match whole words, including when adjacent to Chinese text.
        int offset = normalized.indexOf(anchor);
        while (offset >= 0) {
            int end = offset + anchor.length();
            if ((offset == 0 || !latinOrDigit(anchor.charAt(0)) || !latinOrDigit(normalized.charAt(offset - 1)))
                    && (end == normalized.length() || !latinOrDigit(anchor.charAt(anchor.length() - 1))
                    || !latinOrDigit(normalized.charAt(end)))) {
                return true;
            }
            offset = normalized.indexOf(anchor, offset + 1);
        }
        return false;
    }

    private static boolean latinOrDigit(char value) {
        return Character.UnicodeScript.of(value) == Character.UnicodeScript.LATIN || Character.isDigit(value);
    }

    private static String normalize(String text) {
        return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
