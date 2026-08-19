package com.carya.energynews.collection;

import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NewsCollectionService {

    private final SourceRepository sourceRepository;
    private final RssCollector rssCollector;

    public NewsCollectionService(SourceRepository sourceRepository, RssCollector rssCollector) {
        this.sourceRepository = sourceRepository;
        this.rssCollector = rssCollector;
    }

    public List<CollectedArticle> collect(Source source) {
        return selectCollector(source).collect(source);
    }

    public List<CollectedArticle> collectAllEnabledSources() {
        return sourceRepository.findAllByEnabledTrue().stream()
                .filter(this::isSupported)
                .flatMap(source -> collect(source).stream())
                .toList();
    }

    private NewsCollector selectCollector(Source source) {
        if (source == null) {
            throw new NewsCollectionException("A Source is required for news collection");
        }
        if (source.getType() == null) {
            throw new NewsCollectionException("Source type is required for news collection");
        }

        return switch (source.getType()) {
            case RSS -> rssCollector;
            case API, WEBSITE -> throw unsupportedSourceType(source.getType());
        };
    }

    private boolean isSupported(Source source) {
        return source.getType() == SourceType.RSS;
    }

    private NewsCollectionException unsupportedSourceType(SourceType sourceType) {
        return new NewsCollectionException("Unsupported source type for news collection: " + sourceType);
    }
}
