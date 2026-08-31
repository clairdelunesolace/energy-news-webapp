package com.carya.energynews.dailybriefanalysis;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Matches currency-marked amounts; never computes exchange rates or magnitude conversions. */
final class DailyBriefAiMonetaryGuard {

    private static final String CURRENCY = "(?:[A-Z]{1,3}\\$|\\p{Sc}|元|"
            + Currency.getAvailableCurrencies().stream()
            .flatMap(currency -> Stream.of(currency.getCurrencyCode(), currency.getDisplayName(Locale.SIMPLIFIED_CHINESE)))
            .distinct().sorted(Comparator.comparingInt(String::length).reversed())
            .map(Pattern::quote).collect(Collectors.joining("|")) + ")";
    private static final String NUMBER = "[+-]?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?";
    private static final String MAGNITUDE = "(?:(?i:trillion|billion|million|thousand|hundred|crore|lakh|bn|mn|tn|[kmbt])(?![A-Za-z])"
            + "|万亿|千亿|百亿|十亿|亿|千万|百万|十万|万|千|百|十)";
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![A-Za-z\\d])(?<prefix>" + CURRENCY + ")\\s*(?<prefixNumber>" + NUMBER + ")\\s*"
                    + "(?<prefixMagnitude>" + MAGNITUDE + ")?"
                    + "|(?<!\\d)(?<!\\d[.,])(?<suffixNumber>" + NUMBER + ")\\s*(?<suffixMagnitude>" + MAGNITUDE + ")?\\s*"
                    + "(?<suffix>" + CURRENCY + ")(?![A-Za-z])"
    );

    private DailyBriefAiMonetaryGuard() {
    }

    static void validate(String title, String summary, Collection<DailyBriefAiArticle> supportingArticles) {
        Set<Amount> supported = new HashSet<>();
        for (DailyBriefAiArticle article : supportingArticles) {
            supported.addAll(amounts(article.title()));
            supported.addAll(amounts(article.description()));
        }
        if (!supported.containsAll(amounts(title)) || !supported.containsAll(amounts(summary))) {
            throw new DailyBriefAiValidationException("AI event monetary amounts must match its supporting Articles");
        }
    }

    private static Set<Amount> amounts(String text) {
        Set<Amount> amounts = new HashSet<>();
        if (text == null) {
            return amounts;
        }
        var matcher = AMOUNT.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC));
        while (matcher.find()) {
            String side = matcher.group("prefix") == null ? "suffix" : "prefix";
            String number = new BigDecimal(matcher.group(side + "Number").replace(",", ""))
                    .stripTrailingZeros().toPlainString();
            amounts.add(new Amount(number, magnitude(matcher.group(side + "Magnitude")), matcher.group(side)));
        }
        return amounts;
    }

    private static String magnitude(String value) {
        if (value == null) {
            return "";
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "k", "thousand" -> "thousand";
            case "m", "mn", "million" -> "million";
            case "b", "bn", "billion" -> "billion";
            case "t", "tn", "trillion" -> "trillion";
            default -> value.toLowerCase(Locale.ROOT);
        };
    }

    private record Amount(String number, String magnitude, String currency) {
    }
}
