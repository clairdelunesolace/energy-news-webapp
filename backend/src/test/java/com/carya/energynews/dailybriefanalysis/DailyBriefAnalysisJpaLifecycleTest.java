package com.carya.energynews.dailybriefanalysis;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleRepository;
import com.carya.energynews.dailybrief.DailyBrief;
import com.carya.energynews.dailybrief.DailyBriefItem;
import com.carya.energynews.dailybrief.DailyBriefItemRepository;
import com.carya.energynews.dailybrief.DailyBriefRepository;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import com.carya.energynews.translation.ArticleTranslationRepository;
import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlist.WatchlistRepository;
import com.carya.energynews.watchlistdiscovery.ArticleKeywordMatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.security.admin.password=test-password",
        "app.discovery.provider=none",
        "app.discovery.scheduler.enabled=false",
        "app.daily-brief.ai.provider=none",
        "spring.datasource.url=jdbc:h2:mem:daily-brief-analysis-lifecycle;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
class DailyBriefAnalysisJpaLifecycleTest {

    private static final Instant START = Instant.parse("2026-08-26T16:00:00Z");
    private static final Instant END = Instant.parse("2026-08-27T16:00:00Z");

    @Autowired
    private DailyBriefAnalysisRepository analysisRepository;

    @Autowired
    private DailyBriefEventRepository eventRepository;

    @Autowired
    private DailyBriefEventItemRepository eventItemRepository;

    @Autowired
    private DailyBriefRepository dailyBriefRepository;

    @Autowired
    private DailyBriefItemRepository dailyBriefItemRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private ArticleTranslationRepository translationRepository;

    @Autowired
    private ArticleKeywordMatchRepository matchRepository;

    @BeforeEach
    void clearDatabase() {
        eventItemRepository.deleteAll();
        eventRepository.deleteAll();
        analysisRepository.deleteAll();
        dailyBriefItemRepository.deleteAll();
        dailyBriefRepository.deleteAll();
        translationRepository.deleteAll();
        matchRepository.deleteAll();
        articleRepository.deleteAll();
        watchlistRepository.deleteAll();
        sourceRepository.deleteAll();
    }

    @Test
    void deletingDailyBriefCascadesAllAiRowsButNeverArticle() {
        Snapshot snapshot = saveSnapshot("delete-brief");

        dailyBriefRepository.deleteById(snapshot.briefId());
        dailyBriefRepository.flush();

        assertThat(analysisRepository.count()).isZero();
        assertThat(eventRepository.count()).isZero();
        assertThat(eventItemRepository.count()).isZero();
        assertThat(dailyBriefItemRepository.count()).isZero();
        assertThat(articleRepository.existsById(snapshot.articleId())).isTrue();
    }

    @Test
    void deletingAnalysisCascadesEventsAndLinksButKeepsEvidence() {
        Snapshot snapshot = saveSnapshot("delete-analysis");

        analysisRepository.deleteById(snapshot.analysisId());
        analysisRepository.flush();

        assertThat(eventRepository.count()).isZero();
        assertThat(eventItemRepository.count()).isZero();
        assertThat(dailyBriefRepository.existsById(snapshot.briefId())).isTrue();
        assertThat(dailyBriefItemRepository.count()).isEqualTo(1);
        assertThat(articleRepository.existsById(snapshot.articleId())).isTrue();
    }

    @Test
    void deletingEventCascadesOnlySupportLinks() {
        Snapshot snapshot = saveSnapshot("delete-event");

        eventRepository.deleteById(snapshot.eventId());
        eventRepository.flush();

        assertThat(eventItemRepository.count()).isZero();
        assertThat(analysisRepository.existsById(snapshot.analysisId())).isTrue();
        assertThat(dailyBriefRepository.existsById(snapshot.briefId())).isTrue();
        assertThat(dailyBriefItemRepository.count()).isEqualTo(1);
        assertThat(articleRepository.existsById(snapshot.articleId())).isTrue();
    }

    private Snapshot saveSnapshot(String slug) {
        Source source = sourceRepository.saveAndFlush(new Source(
                "Publisher " + slug,
                "https://" + slug + ".example",
                SourceType.WEBSITE,
                SourcePriority.MEDIUM
        ));
        Watchlist watchlist = watchlistRepository.saveAndFlush(new Watchlist("Watchlist " + slug));
        Article article = articleRepository.saveAndFlush(new Article(
                "Article " + slug,
                "https://publisher.example/" + slug,
                source,
                START.plusSeconds(3_600)
        ));
        DailyBrief brief = dailyBriefRepository.saveAndFlush(new DailyBrief(
                watchlist,
                LocalDate.parse("2026-08-27"),
                "Asia/Shanghai",
                START,
                END,
                1
        ));
        DailyBriefItem item = dailyBriefItemRepository.saveAndFlush(
                new DailyBriefItem(brief, article, 1)
        );
        DailyBriefAnalysis analysis = new DailyBriefAnalysis(brief);
        analysis.update(
                "groq",
                "openai/gpt-oss-20b",
                "标题",
                "概览",
                START.plusSeconds(7_200)
        );
        analysis = analysisRepository.saveAndFlush(analysis);
        DailyBriefEvent event = eventRepository.saveAndFlush(new DailyBriefEvent(
                analysis,
                1,
                "事件",
                "摘要",
                "意义"
        ));
        eventItemRepository.saveAndFlush(new DailyBriefEventItem(event, item, 1));
        return new Snapshot(
                brief.getId(),
                article.getId(),
                analysis.getId(),
                event.getId()
        );
    }

    private record Snapshot(
            Long briefId,
            Long articleId,
            Long analysisId,
            Long eventId
    ) {
    }
}
