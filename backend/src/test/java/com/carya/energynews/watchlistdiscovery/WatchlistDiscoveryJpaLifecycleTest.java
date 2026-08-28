package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.article.ArticleRepository;
import com.carya.energynews.content.ArticleContentFetcher;
import com.carya.energynews.discovery.DiscoveredArticle;
import com.carya.energynews.discovery.NewsDiscoveryProvider;
import com.carya.energynews.discovery.NewsDiscoveryQuery;
import com.carya.energynews.discovery.NewsDiscoveryService;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.translation.ArticleTranslationRepository;
import com.carya.energynews.translation.TranslationOutput;
import com.carya.energynews.translation.TranslationProvider;
import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlist.WatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.security.admin.password=test-password",
        "app.discovery.provider=none",
        "app.discovery.scheduler.enabled=true",
        "app.discovery.scheduler.cron=0 0 0 1 1 *",
        "app.discovery.scheduler.zone=UTC",
        "app.discovery.scheduler.lookback-hours=36",
        "app.discovery.scheduler.limit-per-keyword=5",
        "app.discovery.scheduler.delay-between-keywords-ms=0",
        "app.discovery.scheduler.max-requests-per-run=10",
        "spring.datasource.url=jdbc:h2:mem:watchlist-discovery-jpa;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@Import(WatchlistDiscoveryJpaLifecycleTest.FakeBoundaryConfiguration.class)
class WatchlistDiscoveryJpaLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-08-28T04:00:00Z");

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private WatchlistDiscoveryService discoveryService;

    @Autowired
    private WatchlistDiscoveryScheduler scheduler;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleKeywordMatchRepository matchRepository;

    @Autowired
    private ArticleTranslationRepository translationRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @BeforeEach
    void clearDatabase() {
        translationRepository.deleteAll();
        matchRepository.deleteAll();
        articleRepository.deleteAll();
        watchlistRepository.deleteAll();
        sourceRepository.deleteAll();
    }

    @Test
    void schedulerProcessesDetachedJpaEntitiesWithoutWebRequestContext() {
        Watchlist watchlist = new Watchlist("JPA lifecycle");
        watchlist.addKeyword("battery storage");
        watchlist.addKeyword("grid battery");
        watchlistRepository.saveAndFlush(watchlist);

        ScheduledWatchlistDiscoveryResult result = scheduler.runScheduledDiscovery();

        assertThat(result.watchlistsProcessed()).isEqualTo(1);
        assertThat(result.watchlistsFailed()).isZero();
        assertThat(result.keywordsProcessed()).isEqualTo(2);
        assertThat(result.keywordsFailed()).isZero();
        assertThat(result.saved()).isEqualTo(2);
        assertThat(result.postProcessingAttempted()).isEqualTo(2);
        assertThat(result.metadataTranslationSucceeded()).isEqualTo(2);
        assertThat(result.contentExtractionSucceeded()).isEqualTo(2);
        assertThat(result.contentTranslationSucceeded()).isEqualTo(2);
        assertThat(articleRepository.count()).isEqualTo(2);
        assertThat(matchRepository.count()).isEqualTo(2);
        assertThat(translationRepository.count()).isEqualTo(2);
    }

    @Test
    void manualCoreAlsoProcessesWithoutWebRequestContext() {
        Watchlist watchlist = new Watchlist("Manual JPA lifecycle");
        watchlist.addKeyword("manual battery");
        watchlistRepository.saveAndFlush(watchlist);

        WatchlistDiscoveryRunResponse result = discoveryService.run(
                new WatchlistDiscoveryRunRequest(
                        watchlist.getId(),
                        LocalDate.parse("2026-08-27"),
                        LocalDate.parse("2026-08-28"),
                        5
                )
        );

        assertThat(result.keywordsProcessed()).isEqualTo(1);
        assertThat(result.keywordsFailed()).isZero();
        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.postProcessingAttempted()).isEqualTo(1);
        assertThat(result.contentTranslationSucceeded()).isEqualTo(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeBoundaryConfiguration {

        @Bean
        @Primary
        Clock fixedDiscoveryClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        NewsDiscoveryService fakeNewsDiscoveryService() {
            NewsDiscoveryProvider provider = new NewsDiscoveryProvider() {
                @Override
                public String providerName() {
                    return "fake";
                }

                @Override
                public List<DiscoveredArticle> discover(NewsDiscoveryQuery query) {
                    String slug = query.keyword().replace(' ', '-');
                    return List.of(new DiscoveredArticle(
                            query.keyword() + " project update",
                            "https://publisher.example/" + slug,
                            "Relevant description",
                            "Publisher",
                            NOW.minusSeconds(60),
                            "en"
                    ));
                }
            };
            return new NewsDiscoveryService(provider);
        }

        @Bean
        @Primary
        TranslationProvider fakeTranslationProvider() {
            return input -> input.content() == null
                    ? new TranslationOutput("translated title", "translated description")
                    : new TranslationOutput(null, null, "translated content");
        }

        @Bean
        @Primary
        ArticleContentFetcher fakeArticleContentFetcher() {
            return article -> "Extracted article content";
        }
    }
}
