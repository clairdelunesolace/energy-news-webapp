package com.carya.energynews.dailybrief;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleRepository;
import com.carya.energynews.dailybriefanalysis.DailyBriefAiProvider;
import com.carya.energynews.dailybriefanalysis.DailyBriefAnalysis;
import com.carya.energynews.dailybriefanalysis.DailyBriefAnalysisRepository;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlist.WatchlistRepository;
import com.carya.energynews.watchlistdiscovery.ArticleKeywordMatch;
import com.carya.energynews.watchlistdiscovery.ArticleKeywordMatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;

@SpringBootTest(properties = {
        "app.security.admin.password=test-password",
        "app.discovery.provider=none",
        "app.discovery.scheduler.enabled=false",
        "app.news-sync.cron=-",
        "app.daily-brief.scheduler.enabled=true",
        "app.daily-brief.scheduler.cron=0 0 0 1 1 *",
        "app.daily-brief.scheduler.zone=Asia/Shanghai",
        "app.daily-brief.scheduler.day-offset=-1",
        "app.daily-brief.zone=Asia/Shanghai",
        "app.daily-brief.max-items=1",
        "app.daily-brief.ai.provider=none",
        "spring.datasource.url=jdbc:h2:mem:daily-brief-scheduler;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@Import(DailyBriefSchedulerPersistenceTest.FixedTime.class)
class DailyBriefSchedulerPersistenceTest {

    private static final LocalDate DATE = LocalDate.parse("2026-08-31");
    private static final Instant START = Instant.parse("2026-08-30T16:00:00Z");
    private static final Instant END = Instant.parse("2026-08-31T16:00:00Z");

    @Autowired private DailyBriefScheduler scheduler;
    @Autowired private WatchlistRepository watchlistRepository;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private ArticleKeywordMatchRepository matchRepository;
    @Autowired private DailyBriefRepository briefRepository;
    @Autowired private DailyBriefItemRepository itemRepository;
    @Autowired private DailyBriefAnalysisRepository analysisRepository;
    @Autowired private ObjectProvider<DailyBriefAiProvider> providerSource;
    @MockitoSpyBean private DailyBriefService briefService;

    @Test
    void absentProviderPreservesCommittedBriefsAndRegenerationReusesBriefAndInvalidatesOldAnalysis() {
        Watchlist first = new Watchlist("First scheduled topic");
        first.addKeyword("storage");
        first = watchlistRepository.saveAndFlush(first);
        Watchlist disabled = new Watchlist("Disabled topic");
        disabled.setEnabled(false);
        disabled = watchlistRepository.saveAndFlush(disabled);
        Watchlist empty = watchlistRepository.saveAndFlush(new Watchlist("Empty topic"));
        Source source = sourceRepository.saveAndFlush(new Source("Test publisher", "https://scheduler.example",
                SourceType.WEBSITE, SourcePriority.MEDIUM));
        saveMatchedArticle(first, source, "before", START.minusSeconds(1));
        saveMatchedArticle(first, source, "start", START);
        Article selected = saveMatchedArticle(first, source, "last", END.minusSeconds(1));
        saveMatchedArticle(first, source, "after", END);

        assertThat(providerSource.getIfAvailable()).isNull();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(watchlistRepository.findAllByEnabledTrueOrderByIdAsc())
                .extracting(Watchlist::getId).containsExactly(first.getId(), empty.getId());
        ScheduledDailyBriefResult initialRun = scheduler.runScheduledGeneration();

        assertThat(initialRun.watchlistsProcessed()).isEqualTo(2);
        assertThat(initialRun.watchlistsFailed()).isZero();
        assertThat(initialRun.briefsGenerated()).isEqualTo(2);
        assertThat(initialRun.emptyBriefs()).isEqualTo(1);
        assertThat(initialRun.aiSkipped()).isEqualTo(2);
        assertThat(initialRun.aiGenerated()).isZero();
        assertThat(initialRun.aiFailed()).isZero();
        assertThat(initialRun.schedulerFailed()).isFalse();
        var order = inOrder(briefService);
        order.verify(briefService).generate(new GenerateDailyBriefRequest(first.getId(), DATE, null));
        order.verify(briefService).generate(new GenerateDailyBriefRequest(empty.getId(), DATE, null));
        DailyBriefResponse firstSnapshot = briefService.getByWatchlistAndDate(first.getId(), DATE);
        assertThat(firstSnapshot.windowStart()).isEqualTo(START);
        assertThat(firstSnapshot.windowEnd()).isEqualTo(END);
        assertThat(firstSnapshot.candidateCount()).isEqualTo(2);
        assertThat(firstSnapshot.itemCount()).isEqualTo(1);
        assertThat(firstSnapshot.items()).extracting(DailyBriefItemResponse::articleId).containsExactly(selected.getId());
        assertThat(briefService.getByWatchlistAndDate(empty.getId(), DATE).items()).isEmpty();
        assertThat(briefRepository.findByWatchlistIdAndBriefDate(disabled.getId(), DATE)).isEmpty();
        Long previousItemId = itemRepository.findAllByDailyBriefIdOrderByRankAsc(firstSnapshot.id()).getFirst().getId();

        DailyBriefAnalysis previous = new DailyBriefAnalysis(briefRepository.findById(firstSnapshot.id()).orElseThrow());
        previous.update("test", "local-fake", "先前标题", "先前概览", START);
        previous = analysisRepository.saveAndFlush(previous);
        Long previousAnalysisId = previous.getId();
        clearInvocations(briefService);

        ScheduledDailyBriefResult repeatRun = scheduler.runScheduledGeneration();

        assertThat(repeatRun.briefsGenerated()).isEqualTo(2);
        assertThat(repeatRun.aiSkipped()).isEqualTo(2);
        assertThat(repeatRun.watchlistsFailed()).isZero();
        assertThat(repeatRun.skippedOverlap()).isFalse();
        assertThat(briefRepository.count()).isEqualTo(2);
        assertThat(briefService.getByWatchlistAndDate(first.getId(), DATE).id()).isEqualTo(firstSnapshot.id());
        assertThat(itemRepository.count()).isEqualTo(1);
        assertThat(itemRepository.existsById(previousItemId)).isFalse();
        assertThat(analysisRepository.existsById(previousAnalysisId)).isFalse();
        assertThat(analysisRepository.count()).isZero();
        assertThat(articleRepository.count()).isEqualTo(4);
    }

    private Article saveMatchedArticle(Watchlist watchlist, Source source, String slug, Instant publishedAt) {
        Article article = new Article("Storage update " + slug, "https://scheduler.example/" + slug, source, START);
        article.setPublishedAt(publishedAt);
        article = articleRepository.saveAndFlush(article);
        matchRepository.saveAndFlush(new ArticleKeywordMatch(article, watchlist.getKeywords().getFirst()));
        return article;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTime {
        @Bean
        @Primary
        Clock schedulerTestClock() {
            return Clock.fixed(Instant.parse("2026-09-01T13:00:00Z"), ZoneOffset.UTC);
        }
    }
}
