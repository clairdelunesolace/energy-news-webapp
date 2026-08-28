package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleIngestionService;
import com.carya.energynews.article.ArticleRepository;
import com.carya.energynews.discovery.DiscoveredArticle;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import com.carya.energynews.watchlist.Keyword;
import com.carya.energynews.watchlist.KeywordRepository;
import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlist.WatchlistRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        ArticleIngestionService.class,
        DiscoveryUrlNormalizer.class,
        DiscoverySourceResolver.class,
        WatchlistDiscoveryPersistenceService.class
})
class WatchlistDiscoveryPersistenceServiceTest {

    @Autowired
    private WatchlistDiscoveryPersistenceService persistenceService;

    @Autowired
    private ArticleKeywordMatchRepository matchRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createsDynamicSourceArticleAndIdempotentKeywordMatch() {
        Keyword keyword = saveKeyword("Industry", "battery energy storage");
        DiscoveredArticle discovered = discovered(
                "Battery energy storage project",
                "https://news.example.com/articles/project#details",
                "Example News",
                "en"
        );

        WatchlistDiscoveryPersistenceResult first = persistenceService.ingestAndMatch(
                discovered,
                "https://news.example.com/articles/project",
                keyword.getId()
        );
        WatchlistDiscoveryPersistenceResult second = persistenceService.ingestAndMatch(
                discovered,
                "https://news.example.com/articles/project",
                keyword.getId()
        );

        assertThat(first.status()).isEqualTo(
                WatchlistDiscoveryPersistenceResult.Status.SAVED
        );
        assertThat(first.keywordMatchCreated()).isTrue();
        assertThat(second.status()).isEqualTo(
                WatchlistDiscoveryPersistenceResult.Status.DUPLICATE
        );
        assertThat(second.articleId()).isEqualTo(first.articleId());
        assertThat(second.keywordMatchCreated()).isFalse();
        assertThat(articleRepository.count()).isEqualTo(1);
        assertThat(matchRepository.count()).isEqualTo(1);

        Article article = articleRepository.findById(first.articleId()).orElseThrow();
        assertThat(article.getContent()).isNull();
        assertThat(article.getDescription()).isEqualTo("Relevant description");
        Source source = article.getSource();
        assertThat(source.getName()).isEqualTo("Example News");
        assertThat(source.getUrl()).isEqualTo("https://news.example.com");
        assertThat(source.getType()).isEqualTo(SourceType.WEBSITE);
        assertThat(source.getLanguage()).isEqualTo(SourceLanguage.EN);
        assertThat(source.isEnabled()).isFalse();
        assertThat(source.isContentEnrichmentEnabled()).isFalse();
    }

    @Test
    void reusesExistingSourceAndMatchesCanonicalDuplicateArticle() {
        Source existingSource = sourceRepository.saveAndFlush(new Source(
                "Existing RSS",
                "https://publisher.example/rss",
                SourceType.RSS,
                SourcePriority.HIGH,
                SourceLanguage.EN
        ));
        Article existingArticle = new Article(
                "Existing article",
                "https://publisher.example/article",
                existingSource,
                Instant.parse("2026-08-20T00:00:00Z")
        );
        articleRepository.saveAndFlush(existingArticle);
        Keyword keyword = saveKeyword("Industry", "NVIDIA");
        DiscoveredArticle discovered = discovered(
                "NVIDIA article",
                existingArticle.getUrl(),
                "Provider name differs",
                null
        );

        WatchlistDiscoveryPersistenceResult result = persistenceService.ingestAndMatch(
                discovered,
                existingArticle.getUrl(),
                keyword.getId()
        );

        assertThat(result.status()).isEqualTo(
                WatchlistDiscoveryPersistenceResult.Status.DUPLICATE
        );
        assertThat(result.articleId()).isEqualTo(existingArticle.getId());
        assertThat(result.keywordMatchCreated()).isTrue();
        assertThat(sourceRepository.count()).isEqualTo(1);
        assertThat(matchRepository.existsByArticleIdAndKeywordId(
                existingArticle.getId(),
                keyword.getId()
        )).isTrue();
    }

    @Test
    void oneArticleCanMatchMultipleKeywordsWithoutDuplicateRows() {
        Watchlist watchlist = new Watchlist("Infrastructure");
        Keyword nvidia = watchlist.addKeyword("NVIDIA");
        Keyword voltage = watchlist.addKeyword("800VDC");
        watchlistRepository.saveAndFlush(watchlist);
        DiscoveredArticle discovered = discovered(
                "NVIDIA unveils 800VDC architecture",
                "https://example.com/shared",
                "Example",
                "en"
        );
        WatchlistDiscoveryPersistenceResult first = persistenceService.ingestAndMatch(
                discovered,
                discovered.url(),
                nvidia.getId()
        );

        assertThat(persistenceService.matchExistingArticle(
                first.articleId(),
                voltage.getId()
        )).isTrue();
        assertThat(persistenceService.matchExistingArticle(
                first.articleId(),
                voltage.getId()
        )).isFalse();
        assertThat(matchRepository.countByArticleId(first.articleId())).isEqualTo(2);
    }

    @Test
    void skipsUnknownLanguageOnlyWhenNewSourceWouldBeRequired() {
        Keyword keyword = saveKeyword("Industry", "NVIDIA");
        DiscoveredArticle discovered = discovered(
                "NVIDIA article",
                "https://unknown.example/article",
                "Unknown Publisher",
                null
        );

        WatchlistDiscoveryPersistenceResult result = persistenceService.ingestAndMatch(
                discovered,
                discovered.url(),
                keyword.getId()
        );

        assertThat(result.status()).isEqualTo(
                WatchlistDiscoveryPersistenceResult.Status.SKIPPED_UNSUPPORTED_LANGUAGE
        );
        assertThat(sourceRepository.count()).isZero();
        assertThat(articleRepository.count()).isZero();
        assertThat(matchRepository.count()).isZero();
    }

    @Test
    void deletingWatchlistDeletesKeywordMatchesButPreservesArticle() {
        Watchlist watchlist = new Watchlist("Delete Test");
        Keyword keyword = watchlist.addKeyword("NVIDIA");
        watchlistRepository.saveAndFlush(watchlist);
        WatchlistDiscoveryPersistenceResult result = persistenceService.ingestAndMatch(
                discovered(
                        "NVIDIA article",
                        "https://example.com/delete-test",
                        "Example",
                        "en"
                ),
                "https://example.com/delete-test",
                keyword.getId()
        );

        entityManager.clear();
        Watchlist persistedWatchlist = watchlistRepository.findById(watchlist.getId())
                .orElseThrow();
        watchlistRepository.delete(persistedWatchlist);
        watchlistRepository.flush();

        assertThat(keywordRepository.existsById(keyword.getId())).isFalse();
        assertThat(matchRepository.count()).isZero();
        assertThat(articleRepository.existsById(result.articleId())).isTrue();
    }

    @Test
    void databaseEnforcesUniqueArticleKeywordPair() {
        Source source = sourceRepository.saveAndFlush(new Source(
                "Example",
                "https://example.com",
                SourceType.WEBSITE,
                null,
                SourceLanguage.EN
        ));
        Article article = articleRepository.saveAndFlush(new Article(
                "Article",
                "https://example.com/unique",
                source,
                Instant.now()
        ));
        Keyword keyword = saveKeyword("Unique", "NVIDIA");
        matchRepository.saveAndFlush(new ArticleKeywordMatch(article, keyword));

        assertThatThrownBy(() -> matchRepository.saveAndFlush(
                new ArticleKeywordMatch(article, keyword)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Keyword saveKeyword(String watchlistName, String text) {
        Watchlist watchlist = new Watchlist(watchlistName);
        Keyword keyword = watchlist.addKeyword(text);
        watchlistRepository.saveAndFlush(watchlist);
        return keyword;
    }

    private DiscoveredArticle discovered(
            String title,
            String url,
            String sourceName,
            String languageCode
    ) {
        return new DiscoveredArticle(
                title,
                url,
                "Relevant description",
                sourceName,
                Instant.parse("2026-08-26T10:00:00Z"),
                languageCode
        );
    }
}
