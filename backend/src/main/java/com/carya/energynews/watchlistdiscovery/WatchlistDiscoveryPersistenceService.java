package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleIngestionResult;
import com.carya.energynews.article.ArticleIngestionService;
import com.carya.energynews.article.ArticleRepository;
import com.carya.energynews.collection.CollectedArticle;
import com.carya.energynews.discovery.DiscoveredArticle;
import com.carya.energynews.source.Source;
import com.carya.energynews.watchlist.Keyword;
import com.carya.energynews.watchlist.KeywordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class WatchlistDiscoveryPersistenceService {

    private final DiscoverySourceResolver sourceResolver;
    private final ArticleIngestionService articleIngestionService;
    private final ArticleRepository articleRepository;
    private final KeywordRepository keywordRepository;
    private final ArticleKeywordMatchRepository matchRepository;

    public WatchlistDiscoveryPersistenceService(
            DiscoverySourceResolver sourceResolver,
            ArticleIngestionService articleIngestionService,
            ArticleRepository articleRepository,
            KeywordRepository keywordRepository,
            ArticleKeywordMatchRepository matchRepository
    ) {
        this.sourceResolver = sourceResolver;
        this.articleIngestionService = articleIngestionService;
        this.articleRepository = articleRepository;
        this.keywordRepository = keywordRepository;
        this.matchRepository = matchRepository;
    }

    @Transactional
    public WatchlistDiscoveryPersistenceResult ingestAndMatch(
            DiscoveredArticle discovered,
            String normalizedUrl,
            Long keywordId
    ) {
        Optional<Source> source = sourceResolver.resolve(discovered, normalizedUrl);
        if (source.isEmpty()) {
            return WatchlistDiscoveryPersistenceResult.unsupportedLanguage();
        }

        ArticleIngestionResult ingestion = articleIngestionService.ingest(new CollectedArticle(
                discovered.title(),
                normalizedUrl,
                discovered.description(),
                null,
                discovered.publishedAt(),
                source.get().getId()
        ));
        boolean matchCreated = createMatch(ingestion.article(), keywordId);
        WatchlistDiscoveryPersistenceResult.Status status = switch (ingestion.status()) {
            case SAVED -> WatchlistDiscoveryPersistenceResult.Status.SAVED;
            case DUPLICATE -> WatchlistDiscoveryPersistenceResult.Status.DUPLICATE;
        };
        return new WatchlistDiscoveryPersistenceResult(
                status,
                ingestion.article().getId(),
                matchCreated
        );
    }

    @Transactional
    public boolean matchExistingArticle(Long articleId, Long keywordId) {
        Article article = articleRepository.getReferenceById(articleId);
        return createMatch(article, keywordId);
    }

    private boolean createMatch(Article article, Long keywordId) {
        if (matchRepository.existsByArticleIdAndKeywordId(article.getId(), keywordId)) {
            return false;
        }
        Keyword keyword = keywordRepository.getReferenceById(keywordId);
        matchRepository.saveAndFlush(new ArticleKeywordMatch(article, keyword));
        return true;
    }
}
