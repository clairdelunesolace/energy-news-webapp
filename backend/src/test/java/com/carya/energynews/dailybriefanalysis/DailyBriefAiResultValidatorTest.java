package com.carya.energynews.dailybriefanalysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyBriefAiResultValidatorTest {

    private final DailyBriefAiResultValidator validator = new DailyBriefAiResultValidator();

    @Test
    void validatesAndNormalizesWhitespaceAndDuplicateSupportIds() {
        DailyBriefAiResult result = validator.validate(
                new DailyBriefAiResult(
                        "  管理层标题  ",
                        "  今日概览  ",
                        List.of(new DailyBriefAiEvent(
                                "  事件  ",
                                "  发生了什么  ",
                                "  可能表明本地化产能增加  ",
                                List.of(103L, 103L, 104L)
                        ))
                ),
                confirmedArticles(103L, 104L)
        );

        assertThat(result.headline()).isEqualTo("管理层标题");
        assertThat(result.overview()).isEqualTo("今日概览");
        assertThat(result.events().getFirst().supportingArticleIds())
                .containsExactly(103L, 104L);
    }

    @Test
    void rejectsUnknownOrEmptySupportingArticleIds() {
        assertThatThrownBy(() -> validator.validate(
                result(List.of(999L)),
                confirmedArticles(103L)
        )).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI event references an Article outside the DailyBrief snapshot");

        assertThatThrownBy(() -> validator.validate(
                result(List.of()),
                confirmedArticles(103L)
        )).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI event supportingArticleIds must not be empty");
    }

    @Test
    void rejectsInvalidEventCountAndBlankFields() {
        assertThatThrownBy(() -> validator.validate(
                new DailyBriefAiResult("Headline", "Overview", List.of()),
                confirmedArticles(103L)
        )).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI result must contain between 1 and 5 events");

        List<DailyBriefAiEvent> tooMany = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> new DailyBriefAiEvent(
                        "Event",
                        "Summary",
                        "Why",
                        List.of(103L)
                ))
                .toList();
        assertThatThrownBy(() -> validator.validate(
                new DailyBriefAiResult("Headline", "Overview", tooMany),
                confirmedArticles(103L)
        )).isInstanceOf(DailyBriefAiValidationException.class);

        assertThatThrownBy(() -> validator.validate(
                new DailyBriefAiResult(" ", "Overview", List.of(
                        new DailyBriefAiEvent("Event", "Summary", "Why", List.of(103L))
                )),
                confirmedArticles(103L)
        )).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI headline must not be blank");

        assertThatThrownBy(() -> validator.validate(
                new DailyBriefAiResult("Headline", "Overview", List.of(
                        new DailyBriefAiEvent("Event", " ", "Why", List.of(103L))
                )),
                confirmedArticles(103L)
        )).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI event summary must not be blank");

        assertThatThrownBy(() -> validator.validate(
                new DailyBriefAiResult("Headline", " ", List.of(
                        new DailyBriefAiEvent("Event", "Summary", "Why", List.of(103L))
                )),
                confirmedArticles(103L)
        )).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI overview must not be blank");

        assertThatThrownBy(() -> validator.validate(
                new DailyBriefAiResult("Headline", "Overview", List.of(
                        new DailyBriefAiEvent(" ", "Summary", "Why", List.of(103L))
                )),
                confirmedArticles(103L)
        )).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI event title must not be blank");

        assertThatThrownBy(() -> validator.validate(
                new DailyBriefAiResult("Headline", "Overview", List.of(
                        new DailyBriefAiEvent("Event", "Summary", " ", List.of(103L))
                )),
                confirmedArticles(103L)
        )).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI event whyItMatters must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "reportedly", "reported", "according to", "sources say", "may", "might", "could",
            "considering", "seeks", "plans", "planned", "expected", "proposed", "alleged",
            "REPORTedly", "ACCORDING TO",
            "据报道", "报道称", "消息称", "可能", "或将", "拟", "计划", "预计", "提议", "据称"
    })
    void recognizesUncertaintyMarkersInEitherInputTitleOrDescription(String marker) {
        for (DailyBriefAiArticle source : List.of(
                article(109L, "Company " + marker + " action", null),
                article(109L, "Company news", "Company " + marker + " action")
        )) {
            assertThatThrownBy(() -> validator.validate(result(List.of(109L)), List.of(source)))
                    .isInstanceOf(DailyBriefAiValidationException.class)
                    .hasMessage("AI event must preserve uncertainty from its supporting Articles");
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "NVIDIA reportedly acquires Hugging Face|英伟达并购Hugging Face|英伟达宣布收购Hugging Face。",
            "Sungrow planned a factory in Egypt|Sungrow埃及工厂建成|Sungrow已建成埃及工厂。",
            "Factory opening expected in April 2027|工厂正式投产|工厂已经开始运营。"
    })
    void rejectsReportedPlannedAndExpectedClaimsUpgradedToFacts(
            String inputTitle,
            String eventTitle,
            String eventSummary
    ) {
        DailyBriefAiResult output = eventResult(eventTitle, eventSummary, List.of(109L));

        assertThatThrownBy(() -> validator.validate(
                output,
                List.of(article(109L, inputTitle, null))
        )).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI event must preserve uncertainty from its supporting Articles");
    }

    @Test
    void rejectsTheLiveArticle109FailureEvenWhenWhyItMattersIsCautious() {
        DailyBriefAiArticle source = article(
                109L,
                "英伟达已主导人工智能芯片市场。据报道，如今它还将收购Hugging Face。",
                "据报道，129亿美元的收购价几乎是Hugging Face在2023年融资轮中的估值的三倍。"
        );

        assertThatThrownBy(() -> validator.validate(
                eventResult("英伟达并购Hugging Face", "英伟达以129亿美元收购Hugging Face。", List.of(109L)),
                List.of(source)
        )).isInstanceOf(DailyBriefAiValidationException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "据报道英伟达拟收购Hugging Face|市场关注交易后续披露。",
            "英伟达交易动向|报道称英伟达可能收购Hugging Face。"
    })
    void acceptsUncertaintyInEventTitleOrSummary(String title, String summary) {
        DailyBriefAiResult output = eventResult(title, summary, List.of(109L));

        assertThat(validator.validate(
                output,
                List.of(article(109L, "NVIDIA reportedly acquires Hugging Face", null))
        )).isEqualTo(output);
    }

    @Test
    void requiresUncertaintyWhenEveryCitedArticleIsFlaggedButDoesNotUseUncitedEvidence() {
        List<DailyBriefAiArticle> snapshot = List.of(
                article(109L, "Company plans an acquisition", null),
                article(110L, "Company news", "据报道交易正在推进。"),
                article(103L, "Company confirms an acquisition", null)
        );

        assertThatThrownBy(() -> validator.validate(result(List.of(109L, 110L)), snapshot))
                .isInstanceOf(DailyBriefAiValidationException.class);
        assertThat(validator.validate(result(List.of(109L, 103L)), snapshot))
                .isEqualTo(result(List.of(109L, 103L)));
    }

    @Test
    void permitsConfirmedConstructionWithExpectedCommissioningButRejectsCompletedFactory() {
        List<DailyBriefAiArticle> snapshot = List.of(article(
                112L,
                "Sungrow在埃及为一座10 GWh的电池储能工厂举行奠基仪式",
                "Sungrow已开始在埃及建设工厂，该工厂计划年产能达10 GWh，预计于2027年4月投产。"
        ));
        DailyBriefAiResult faithful = eventResult(
                "Sungrow埃及储能工厂开工",
                "工厂已开始建设，预计于2027年4月投产。",
                List.of(112L)
        );

        assertThat(validator.validate(faithful, snapshot)).isEqualTo(faithful);
        assertThatThrownBy(() -> validator.validate(
                eventResult("埃及储能工厂建成投产", "工厂已建成并投入运营。", List.of(112L)),
                snapshot
        )).isInstanceOf(DailyBriefAiValidationException.class);
    }

    @Test
    void doesNotTreatEnglishSubstringsOrCommonChineseCompoundsAsUncertainty() {
        DailyBriefAiResult output = result(List.of(103L));

        assertThat(validator.validate(output, List.of(article(
                103L,
                "Mayfair completes an unplanned repair",
                "项目采用虚拟电厂模拟与拟合技术。"
        )))).isEqualTo(output);
    }

    private List<DailyBriefAiArticle> confirmedArticles(Long... ids) {
        return Arrays.stream(ids).map(id -> article(id, "Confirmed company news", null)).toList();
    }

    private DailyBriefAiArticle article(Long id, String title, String description) {
        return new DailyBriefAiArticle(id, title, description, "Publisher", null, null, List.of());
    }

    private DailyBriefAiResult eventResult(String title, String summary, List<Long> articleIds) {
        return new DailyBriefAiResult("管理层标题", "整体概览", List.of(new DailyBriefAiEvent(
                title,
                summary,
                "如果交易最终完成，可能进一步扩大其覆盖范围。",
                articleIds
        )));
    }

    private DailyBriefAiResult result(List<Long> supportingArticleIds) {
        return new DailyBriefAiResult(
                "Headline",
                "Overview",
                List.of(new DailyBriefAiEvent(
                        "Event",
                        "Summary",
                        "Why",
                        supportingArticleIds
                ))
        );
    }
}
