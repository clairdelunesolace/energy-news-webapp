package com.carya.energynews.dailybriefanalysis;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleRepository;
import com.carya.energynews.dailybrief.DailyBrief;
import com.carya.energynews.dailybrief.DailyBriefItem;
import com.carya.energynews.dailybrief.DailyBriefItemRepository;
import com.carya.energynews.dailybrief.DailyBriefRepository;
import com.carya.energynews.dailybrief.DailyBriefResponse;
import com.carya.energynews.dailybrief.DailyBriefService;
import com.carya.energynews.dailybrief.GenerateDailyBriefRequest;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import com.carya.energynews.translation.ArticleTranslation;
import com.carya.energynews.translation.ArticleTranslationRepository;
import com.carya.energynews.translation.TranslationLanguage;
import com.carya.energynews.translation.TranslationStatus;
import com.carya.energynews.watchlist.Keyword;
import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlist.WatchlistRepository;
import com.carya.energynews.watchlistdiscovery.ArticleKeywordMatch;
import com.carya.energynews.watchlistdiscovery.ArticleKeywordMatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.security.admin.password=test-password",
        "app.discovery.provider=none",
        "app.discovery.scheduler.enabled=false",
        "app.daily-brief.zone=Asia/Shanghai",
        "app.daily-brief.max-items=10",
        "app.daily-brief.ai.provider=none",
        "spring.datasource.url=jdbc:h2:mem:daily-brief-analysis-service;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@Import(DailyBriefAnalysisServiceTest.FakeAiConfiguration.class)
class DailyBriefAnalysisServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T06:00:00Z");
    private static final LocalDate BRIEF_DATE = LocalDate.parse("2026-08-27");
    private static final Instant WINDOW_START = Instant.parse("2026-08-26T16:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-08-27T16:00:00Z");

    @Autowired
    private DailyBriefAnalysisService analysisService;

    @Autowired
    private DailyBriefService dailyBriefService;

    @Autowired
    private FakeDailyBriefAiProvider provider;

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
    private WatchlistRepository watchlistRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleTranslationRepository translationRepository;

    @Autowired
    private ArticleKeywordMatchRepository matchRepository;

    @BeforeEach
    void clearDatabase() {
        provider.reset();
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
    void createsChineseFirstAnalysisWithOrderedEventsAndSnapshotItemLinks() {
        Fixture fixture = saveBrief(true);
        provider.result = result(
                "AI管理层标题",
                List.of(
                        new DailyBriefAiEvent(
                                "事件一",
                                "据报道，项目可能推进。",
                                "如果推进，可能表明市场需求增加。",
                                List.of(fixture.firstArticleId(), fixture.secondArticleId())
                        ),
                        new DailyBriefAiEvent(
                                "事件二",
                                "第二项进展。",
                                "值得关注的是产能扩展。",
                                List.of(fixture.secondArticleId())
                        )
                )
        );

        DailyBriefAnalysisResponse response = analysisService.generate(fixture.briefId());

        assertThat(provider.calls).isEqualTo(1);
        assertThat(provider.transactionActiveDuringCall).isFalse();
        assertThat(provider.lastRequest.watchlistName()).isEqualTo("Analysis Watchlist");
        assertThat(provider.lastRequest.articles().getFirst().title()).isEqualTo("中文标题");
        assertThat(provider.lastRequest.articles().getFirst().description()).isEqualTo("中文描述");
        assertThat(provider.lastRequest.articles().getFirst().matchedKeywords())
                .containsExactly("NVIDIA");
        assertThat(response.dailyBriefId()).isEqualTo(fixture.briefId());
        assertThat(response.provider()).isEqualTo("fake-ai");
        assertThat(response.model()).isEqualTo("fake-model");
        assertThat(response.generatedAt()).isEqualTo(NOW);
        assertThat(response.events())
                .extracting(DailyBriefEventResponse::rank)
                .containsExactly(1, 2);
        assertThat(response.events().getFirst().supportingArticleIds())
                .containsExactly(fixture.firstArticleId(), fixture.secondArticleId());
        assertThat(analysisRepository.count()).isEqualTo(1);
        assertThat(eventRepository.count()).isEqualTo(2);
        assertThat(eventItemRepository.count()).isEqualTo(3);

        DailyBriefAnalysisResponse stored = analysisService.get(fixture.briefId());
        assertThat(stored.id()).isEqualTo(response.id());
        assertThat(stored.headline()).isEqualTo("AI管理层标题");
        assertThat(stored.events()).isEqualTo(response.events());
    }

    @Test
    void successfulRegenerationReusesAnalysisAndAtomicallyReplacesEvents() {
        Fixture fixture = saveBrief(true);
        provider.result = result("第一版", List.of(new DailyBriefAiEvent(
                "旧事件",
                "旧摘要",
                "旧意义",
                List.of(fixture.firstArticleId(), fixture.secondArticleId())
        )));
        DailyBriefAnalysisResponse first = analysisService.generate(fixture.briefId());

        provider.result = result("第二版", List.of(new DailyBriefAiEvent(
                "新事件",
                "新摘要",
                "新意义",
                List.of(fixture.secondArticleId())
        )));
        DailyBriefAnalysisResponse second = analysisService.generate(fixture.briefId());

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.headline()).isEqualTo("第二版");
        assertThat(second.events()).hasSize(1);
        assertThat(second.events().getFirst().title()).isEqualTo("新事件");
        assertThat(analysisRepository.count()).isEqualTo(1);
        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(eventItemRepository.count()).isEqualTo(1);
    }

    @Test
    void providerAndBusinessValidationFailuresPreserveExistingAnalysis() {
        Fixture fixture = saveBrief(true);
        provider.result = result("保留版本", List.of(new DailyBriefAiEvent(
                "保留事件",
                "保留摘要",
                "保留意义",
                List.of(fixture.firstArticleId())
        )));
        DailyBriefAnalysisResponse existing = analysisService.generate(fixture.briefId());

        provider.failure = new DailyBriefAiException(
                DailyBriefAiException.Failure.RATE_LIMITED,
                "Groq daily brief request was rate limited"
        );
        assertThatThrownBy(() -> analysisService.generate(fixture.briefId()))
                .isInstanceOf(DailyBriefAiException.class);
        assertStoredAnalysisUnchanged(fixture.briefId(), existing);

        provider.failure = null;
        provider.result = result("不应保存", List.of(new DailyBriefAiEvent(
                "无效事件",
                "无效摘要",
                "无效意义",
                List.of(999_999L)
        )));
        assertThatThrownBy(() -> analysisService.generate(fixture.briefId()))
                .isInstanceOf(DailyBriefAiValidationException.class);
        assertStoredAnalysisUnchanged(fixture.briefId(), existing);
    }

    @Test
    void rejectsLostUncertaintyBeforePersistenceAndPreservesPreviousSuccessfulAnalysis() {
        Fixture fixture = saveBrief(true);
        ArticleTranslation translatedEvidence = translationRepository
                .findByArticleIdAndLanguage(fixture.firstArticleId(), TranslationLanguage.ZH_CN)
                .orElseThrow();
        translatedEvidence.setTitle("据报道英伟达拟收购Hugging Face");
        translatedEvidence.setDescription("据报道，收购价为129亿美元。");
        translationRepository.saveAndFlush(translatedEvidence);

        DailyBriefAiResult invalidResult = result("不应保存", List.of(
                new DailyBriefAiEvent(
                        "其他进展", "已披露的进展。", "值得关注。", List.of(fixture.secondArticleId())
                ),
                new DailyBriefAiEvent(
                        "英伟达并购Hugging Face",
                        "英伟达宣布以129亿美元收购Hugging Face。",
                        "如果交易最终完成，可能扩大其覆盖范围。",
                        List.of(fixture.firstArticleId())
                )
        ));
        provider.result = invalidResult;

        assertThatThrownBy(() -> analysisService.generate(fixture.briefId()))
                .isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI event must preserve uncertainty from its supporting Articles");
        assertThat(provider.lastRequest.articles().getFirst().title())
                .isEqualTo("据报道英伟达拟收购Hugging Face");
        assertThat(analysisRepository.count()).isZero();
        assertThat(eventRepository.count()).isZero();
        assertThat(eventItemRepository.count()).isZero();

        provider.result = new DailyBriefAiResult(
                "据报道英伟达拟推进收购",
                "媒体报道称英伟达可能推进交易，仍待进一步披露。",
                List.of(new DailyBriefAiEvent(
                        "据报道英伟达拟收购Hugging Face",
                        "报道称英伟达可能以129亿美元收购Hugging Face。",
                        "如果交易最终完成，可能扩大其覆盖范围。",
                        List.of(fixture.firstArticleId())
                ))
        );
        analysisService.generate(fixture.briefId());
        DailyBriefAnalysisResponse existing = analysisService.get(fixture.briefId());

        provider.result = invalidResult;
        assertThatThrownBy(() -> analysisService.generate(fixture.briefId()))
                .isInstanceOf(DailyBriefAiValidationException.class);

        assertThat(analysisService.get(fixture.briefId())).isEqualTo(existing);
        assertStoredAnalysisUnchanged(fixture.briefId(), existing);
    }

    @Test
    void globalUncertaintyAndMonetaryFailuresPreserveTheEntireStoredAnalysis() {
        Fixture fixture = saveBrief(true);
        ArticleTranslation evidence = translationRepository
                .findByArticleIdAndLanguage(fixture.firstArticleId(), TranslationLanguage.ZH_CN)
                .orElseThrow();
        evidence.setTitle("据报道星海集团拟收购Kepler Systems");
        evidence.setDescription("据报道，交易金额为$14.6 million。");
        translationRepository.saveAndFlush(evidence);
        DailyBriefAiEvent faithfulEvent = new DailyBriefAiEvent(
                "星海集团计划收购Kepler Systems", "据报道，交易金额为$14.6 million。",
                "如果交易最终完成，可能扩大其覆盖范围。", List.of(fixture.firstArticleId())
        );
        provider.result = new DailyBriefAiResult("交易动向", "据报道，星海集团拟收购Kepler Systems。", List.of(faithfulEvent));
        analysisService.generate(fixture.briefId());
        DailyBriefAnalysisResponse existing = analysisService.get(fixture.briefId());

        for (DailyBriefAiResult invalid : List.of(
                new DailyBriefAiResult("交易动向", "星海集团已收购Kepler Systems。", List.of(faithfulEvent)),
                new DailyBriefAiResult("Kepler Systems已被收购", "据报道，交易仍待确认。", List.of(faithfulEvent)),
                result("交易动向", List.of(new DailyBriefAiEvent(
                        faithfulEvent.title(), "据报道，交易金额为14.6百万美元。",
                        faithfulEvent.whyItMatters(), faithfulEvent.supportingArticleIds()
                )))
        )) {
            provider.result = invalid;
            assertThatThrownBy(() -> analysisService.generate(fixture.briefId()))
                    .isInstanceOf(DailyBriefAiValidationException.class);
            assertThat(analysisService.get(fixture.briefId())).isEqualTo(existing);
            assertStoredAnalysisUnchanged(fixture.briefId(), existing);
        }
    }

    @Test
    void emptyBriefSkipsProviderAndDeterministicRegenerationInvalidatesAnalysis() {
        Fixture empty = saveBrief(false);
        assertThatThrownBy(() -> analysisService.generate(empty.briefId()))
                .isInstanceOf(DailyBriefEmptyAnalysisException.class);
        assertThat(provider.calls).isZero();

        clearDatabase();
        Fixture fixture = saveBrief(true);
        provider.result = result("将失效", List.of(new DailyBriefAiEvent(
                "事件",
                "摘要",
                "意义",
                List.of(fixture.firstArticleId())
        )));
        analysisService.generate(fixture.briefId());

        DailyBriefResponse regenerated = dailyBriefService.generate(
                new GenerateDailyBriefRequest(fixture.watchlistId(), BRIEF_DATE, 10)
        );

        assertThat(regenerated.id()).isEqualTo(fixture.briefId());
        assertThat(analysisRepository.count()).isZero();
        assertThat(eventRepository.count()).isZero();
        assertThat(eventItemRepository.count()).isZero();
        assertThatThrownBy(() -> analysisService.get(fixture.briefId()))
                .isInstanceOf(DailyBriefAnalysisNotFoundException.class);
    }

    private void assertStoredAnalysisUnchanged(
            Long dailyBriefId,
            DailyBriefAnalysisResponse expected
    ) {
        DailyBriefAnalysisResponse stored = analysisService.get(dailyBriefId);
        assertThat(stored.id()).isEqualTo(expected.id());
        assertThat(stored.headline()).isEqualTo(expected.headline());
        assertThat(stored.events()).isEqualTo(expected.events());
        assertThat(analysisRepository.count()).isEqualTo(1);
        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(eventItemRepository.count()).isEqualTo(1);
    }

    private Fixture saveBrief(boolean withItems) {
        Source source = sourceRepository.saveAndFlush(new Source(
                "Analysis Publisher",
                "https://analysis-publisher.example",
                SourceType.WEBSITE,
                SourcePriority.MEDIUM
        ));
        Watchlist watchlist = new Watchlist("Analysis Watchlist");
        Keyword keyword = watchlist.addKeyword("NVIDIA");
        watchlistRepository.saveAndFlush(watchlist);

        DailyBrief brief = dailyBriefRepository.saveAndFlush(new DailyBrief(
                watchlist,
                BRIEF_DATE,
                "Asia/Shanghai",
                WINDOW_START,
                WINDOW_END,
                withItems ? 2 : 0
        ));
        if (!withItems) {
            return new Fixture(brief.getId(), watchlist.getId(), null, null);
        }

        Article first = articleRepository.saveAndFlush(new Article(
                "Original title",
                "https://analysis-publisher.example/first",
                source,
                WINDOW_START.plusSeconds(3_600)
        ));
        first.setDescription("Original description");
        first.setPublishedAt(WINDOW_START.plusSeconds(3_600));
        articleRepository.saveAndFlush(first);
        Article second = articleRepository.saveAndFlush(new Article(
                "Second original",
                "https://analysis-publisher.example/second",
                source,
                WINDOW_START.plusSeconds(7_200)
        ));
        second.setDescription("Second description");
        articleRepository.saveAndFlush(second);

        matchRepository.saveAllAndFlush(List.of(
                new ArticleKeywordMatch(first, keyword),
                new ArticleKeywordMatch(second, keyword)
        ));
        ArticleTranslation translation = new ArticleTranslation(
                first,
                TranslationLanguage.ZH_CN
        );
        translation.setTitle("中文标题");
        translation.setDescription("中文描述");
        translation.setStatus(TranslationStatus.SUCCESS);
        translationRepository.saveAndFlush(translation);

        dailyBriefItemRepository.saveAllAndFlush(List.of(
                new DailyBriefItem(brief, first, 1),
                new DailyBriefItem(brief, second, 2)
        ));
        return new Fixture(brief.getId(), watchlist.getId(), first.getId(), second.getId());
    }

    private DailyBriefAiResult result(String headline, List<DailyBriefAiEvent> events) {
        return new DailyBriefAiResult(headline, "今日整体概览。", events);
    }

    private record Fixture(
            Long briefId,
            Long watchlistId,
            Long firstArticleId,
            Long secondArticleId
    ) {
    }

    static class FakeDailyBriefAiProvider implements DailyBriefAiProvider {

        private DailyBriefAiResult result;
        private RuntimeException failure;
        private DailyBriefAiRequest lastRequest;
        private int calls;
        private boolean transactionActiveDuringCall;

        @Override
        public String providerName() {
            return "fake-ai";
        }

        @Override
        public String model() {
            return "fake-model";
        }

        @Override
        public DailyBriefAiResult analyze(DailyBriefAiRequest request) {
            calls++;
            lastRequest = request;
            transactionActiveDuringCall = TransactionSynchronizationManager
                    .isActualTransactionActive();
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        private void reset() {
            result = null;
            failure = null;
            lastRequest = null;
            calls = 0;
            transactionActiveDuringCall = false;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeAiConfiguration {

        @Bean
        FakeDailyBriefAiProvider fakeDailyBriefAiProvider() {
            return new FakeDailyBriefAiProvider();
        }

        @Bean
        @Primary
        Clock fixedDailyBriefAnalysisClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
