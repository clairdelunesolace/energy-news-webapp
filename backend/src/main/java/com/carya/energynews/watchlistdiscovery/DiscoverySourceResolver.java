package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.discovery.DiscoveredArticle;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class DiscoverySourceResolver {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final SourceRepository sourceRepository;
    private final DiscoveryUrlNormalizer urlNormalizer;

    public DiscoverySourceResolver(
            SourceRepository sourceRepository,
            DiscoveryUrlNormalizer urlNormalizer
    ) {
        this.sourceRepository = sourceRepository;
        this.urlNormalizer = urlNormalizer;
    }

    public Optional<Source> resolve(DiscoveredArticle article, String normalizedUrl) {
        String articleHost = urlNormalizer.hostKey(normalizedUrl);
        String sourceName = normalizeName(article.sourceName());

        Optional<Source> existing = sourceRepository.findAll().stream()
                .filter(source -> sameHost(source.getUrl(), articleHost)
                        || sameName(source.getName(), sourceName))
                .findFirst();
        if (existing.isPresent()) {
            return existing;
        }

        SourceLanguage language = mapLanguage(article.languageCode());
        if (language == null) {
            return Optional.empty();
        }

        String origin = urlNormalizer.origin(normalizedUrl);
        String name = article.sourceName() == null || article.sourceName().isBlank()
                ? articleHost
                : article.sourceName().strip();
        Source source = new Source(name, origin, SourceType.WEBSITE, null, language, false);
        source.setEnabled(false);
        return Optional.of(sourceRepository.saveAndFlush(source));
    }

    private boolean sameHost(String sourceUrl, String articleHost) {
        return articleHost != null && articleHost.equals(urlNormalizer.hostKey(sourceUrl));
    }

    private boolean sameName(String existingName, String candidateName) {
        return candidateName != null && candidateName.equals(normalizeName(existingName));
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return WHITESPACE.matcher(value.strip().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }

    private SourceLanguage mapLanguage(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return null;
        }
        return switch (languageCode.strip().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "en" -> SourceLanguage.EN;
            case "zh", "zh-cn", "zh-hans" -> SourceLanguage.ZH_CN;
            default -> null;
        };
    }
}
