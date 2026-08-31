package com.carya.energynews.dailybriefanalysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyBriefAiEvidenceFidelityTest {

    private final DailyBriefAiResultValidator validator = new DailyBriefAiResultValidator();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "109|103|英伟达|Hugging Face",
            "7301|8412|Atlas Robotics|Northwind Labs",
            "9017|6621|海川集团|Silver Orchard"
    })
    void uncertainEventMustStayQualifiedInEachGlobalField(
            long uncertainId, long confirmedId, String company, String target
    ) {
        List<DailyBriefAiArticle> sources = List.of(
                article(uncertainId, company + "据报道拟收购" + target, "媒体仍待后续披露。"),
                article(confirmedId, company + "数据中心业务扩张", "能源基础设施面临挑战。")
        );
        List<DailyBriefAiEvent> events = List.of(
                event(company + "计划收购" + target, "据报道，交易仍待确认。", uncertainId),
                event(company + "数据中心业务扩张", "能源基础设施面临挑战。", confirmedId)
        );
        String unrelatedHeadline = company + "数据中心业务扩张，能源基础设施面临挑战";
        String certainClaim = company + "正通过收购" + target + "进一步扩大业务。";
        String qualifiedClaim = "据报道，" + company + "拟收购" + target + "。";

        assertThatThrownBy(() -> validator.validate(new DailyBriefAiResult(
                unrelatedHeadline, certainClaim, events
        ), sources)).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI overview must preserve uncertainty for a referenced event");
        assertThatThrownBy(() -> validator.validate(new DailyBriefAiResult(
                certainClaim, qualifiedClaim, events
        ), sources)).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI headline must preserve uncertainty for a referenced event");

        DailyBriefAiResult unrelated = new DailyBriefAiResult(unrelatedHeadline, qualifiedClaim, events);
        assertThat(validator.validate(unrelated, sources)).isEqualTo(unrelated);
        DailyBriefAiResult qualified = new DailyBriefAiResult(qualifiedClaim, qualifiedClaim, events);
        assertThat(validator.validate(qualified, sources)).isEqualTo(qualified);
    }

    @Test
    void liveOverviewRegressionUsesActualChineseMetadata() {
        DailyBriefAiArticle source = article(109L,
                "英伟达已主导人工智能芯片市场。据报道，如今它还将收购Hugging Face。",
                "据报道，129亿美元的收购价几乎是Hugging Face在2023年融资轮中的估值的三倍。"
        );
        DailyBriefAiEvent event = event("英伟达计划收购Hugging Face",
                "据报道，英伟达可能以129亿美元收购Hugging Face。", 109L);

        assertThatThrownBy(() -> validator.validate(new DailyBriefAiResult(
                "行业动态", "英伟达正通过收购Hugging Face进一步巩固AI芯片市场。", List.of(event)
        ), List.of(source))).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI overview must preserve uncertainty for a referenced event");
    }

    @ParameterizedTest
    @ValueSource(strings = {"据报道，其他项目可能延期。", "据报道，其他项目可能延期至2028. ", "Reportedly, other projects may be delayed. "})
    void qualifierInAnotherSentenceCannotQualifyDefinitiveRepetition(String precedingSentence) {
        DailyBriefAiArticle source = article(1703L, "Orion reportedly considers Blue Harbor", null);
        DailyBriefAiEvent event = event("Orion计划收购Blue Harbor", "据报道，交易尚待确认。", 1703L);

        assertThatThrownBy(() -> validator.validate(new DailyBriefAiResult(
                "其他进展", precedingSentence + "Orion完成收购blue   harbor。", List.of(event)
        ), List.of(source))).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI overview must preserve uncertainty for a referenced event");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "天穹考虑扩建星港储能工厂|天穹计划扩建星港储能工厂|星港储能工厂已完成扩建。",
            "据报道企业拟推出“远航一号”|企业计划推出“远航一号”|远航一号已经发布。",
            "Regulator reportedly considers permits for orbital launches|Regulator plans permits for orbital launches|Regulator approved permits for orbital launches."
    })
    void linksChineseQuotedAndEnglishClaimPhrasesWithoutAnEntityDictionary(
            String evidence, String title, String overview
    ) {
        // The Chinese evidence uses a recognized source uncertainty marker in its description.
        DailyBriefAiArticle source = article(5817L, evidence, "据报道，后续仍待确认。");
        DailyBriefAiEvent event = event(title, "据报道，后续仍待确认。", 5817L);

        assertThatThrownBy(() -> validator.validate(new DailyBriefAiResult(
                "其他进展", overview, List.of(event)
        ), List.of(source))).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI overview must preserve uncertainty for a referenced event");
        DailyBriefAiResult qualified = new DailyBriefAiResult("其他进展", "据报道，" + overview, List.of(event));
        assertThat(validator.validate(qualified, List.of(source))).isEqualTo(qualified);
    }

    @Test
    void ignoresUnrepresentedStoriesAndAmbiguousSharedNamesAndUsesWholeWords() {
        List<DailyBriefAiArticle> sources = List.of(
                article(17L, "Vector Labs reportedly considers Copper Grove", null),
                article(28L, "Vector Labs opens a factory", null)
        );
        DailyBriefAiEvent uncertain = event("Vector Labs计划收购Copper Grove", "据报道，仍待确认。", 17L);
        DailyBriefAiResult unrelated = new DailyBriefAiResult("Vector Labs工厂开业",
                "Copper Groves是另一项目。", List.of(uncertain));
        assertThat(validator.validate(unrelated, sources)).isEqualTo(unrelated);

        DailyBriefAiResult omitted = new DailyBriefAiResult("Vector Labs工厂开业", "工厂已开业。", List.of(
                event("工厂开业", "工厂已开业。", 28L)
        ));
        assertThat(validator.validate(omitted, sources)).isEqualTo(omitted);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "$96.2 billion|96.2亿美元",
            "$108B|108亿美元",
            "$96.2 billion|962亿美元",
            "€7.4 million|740万欧元",
            "£2.5 billion|£2500 million",
            "₹32 crore|₹320 million",
            "JPY900 million|JPY9亿",
            "129亿美元|129美元",
            "129亿美元|130亿美元",
            "₩4.8 trillion|₩4800 billion",
            "500万元|5百万元",
            "$12 million|€12 million",
            "USD18.5m|USD185m",
            "$4,200|$4.2 thousand"
    })
    void rejectsUnsupportedMonetaryNumbersCurrenciesAndMagnitudeConversionsInBothEventFields(
            String sourceAmount, String outputAmount
    ) {
        List<DailyBriefAiArticle> sources = List.of(article(7231L, "项目披露", "金额为" + sourceAmount + "。"));
        for (DailyBriefAiEvent event : List.of(
                event("项目金额为" + outputAmount, "项目进展已披露。", 7231L),
                event("项目进展", "项目金额为" + outputAmount + "。", 7231L)
        )) {
            assertThatThrownBy(() -> validator.validate(output(event), sources))
                    .isInstanceOf(DailyBriefAiValidationException.class)
                    .hasMessage("AI event monetary amounts must match its supporting Articles");
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "$108B|$108 billion",
            "$96.2 billion|$96.2 billion",
            "$108B|$108B",
            "129亿美元|129亿美元",
            "€7.4M|€7.4 million",
            "GBP2.50 billion|GBP2.5bn",
            "₹32 crore|₹32 crore",
            "USD18.5m|USD18.5 million",
            "$1,250.00|$1250",
            "500万元|500万元",
            "₩4.8 trillion|₩4.8T",
            "CAD-2.5 million|CAD-2.50M",
            "３２万元|32万元",
            "CHF6 thousand|CHF6k"
    })
    void preservesSourceAmountsAndOnlyNormalizesHarmlessFormatting(String sourceAmount, String outputAmount) {
        DailyBriefAiResult output = output(event("项目金额为" + outputAmount, "金额为" + outputAmount + "。", 4281L));
        for (DailyBriefAiArticle source : List.of(
                article(4281L, "金额为" + sourceAmount, null),
                article(4281L, "项目披露", "金额为" + sourceAmount + "。")
        )) {
            assertThat(validator.validate(output, List.of(source))).isEqualTo(output);
        }
    }

    @Test
    void permitsAConvertedRepresentationOnlyWhenItIsAlsoSuppliedByCitedEvidence() {
        DailyBriefAiResult output = output(event("项目金额为962亿美元", "披露的金额为962亿美元。", 512L));
        List<DailyBriefAiArticle> sources = List.of(article(512L, "财务披露", "$96.2 billion（962亿美元）。"));

        assertThat(validator.validate(output, sources)).isEqualTo(output);
    }

    @Test
    void neitherUncitedArticlesNorOtherEventsCanSupportAnIntroducedAmount() {
        List<DailyBriefAiArticle> sources = List.of(
                article(317L, "设备交付", "项目已披露。"),
                article(428L, "其他投资", "金额为€25 million。")
        );
        DailyBriefAiEvent invalid = event("设备交付", "金额为€25 million。", 317L);
        DailyBriefAiEvent valid = event("其他投资", "金额为€25 million。", 428L);

        assertThatThrownBy(() -> validator.validate(new DailyBriefAiResult(
                "项目进展", "项目已披露。", List.of(valid, invalid)
        ), sources)).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI event monetary amounts must match its supporting Articles");
    }

    @Test
    void duplicateUncertainCoverageCannotEraseCitedClaimAnchors() {
        List<DailyBriefAiArticle> sources = List.of(
                article(345L, "据报道，公司拟推出“海潮系列”", null),
                article(456L, "消息称，公司计划推出“海潮系列”", null)
        );
        DailyBriefAiEvent event = event("据报道，公司拟推出“海潮系列”", "仍待后续披露。", 345L);
        assertThatThrownBy(() -> validator.validate(new DailyBriefAiResult(
                "产品进展", "海潮系列已经上市。", List.of(event)
        ), sources)).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI overview must preserve uncertainty for a referenced event");
    }

    @Test
    void blankQuotesAndGenericProperCaseWordsAreNotClaimAnchors() {
        DailyBriefAiArticle source = article(6501L, "The Company reportedly expands", "据报道，项目代号为“   ”。");
        DailyBriefAiResult output = new DailyBriefAiResult("The Company业务动态", "行业发展。", List.of(
                event("The Company计划扩展", "据报道，项目代号为“   ”。", 6501L)
        ));
        assertThat(validator.validate(output, List.of(source))).isEqualTo(output);
    }

    @ParameterizedTest
    @ValueSource(strings = {"金额为", "财务披露，", "财务披露,", "披露：", "披露。", "披露：\n"})
    void punctuationBeforeAnAmountDoesNotChangeItsRecognition(String prefix) {
        DailyBriefAiArticle source = article(2803L, "资金披露", prefix + "73.4万欧元用于项目建设。");
        DailyBriefAiResult faithful = output(event("项目资金", "金额为73.4万欧元。", 2803L));
        assertThat(validator.validate(faithful, List.of(source))).isEqualTo(faithful);

        assertThatThrownBy(() -> validator.validate(output(event(
                "项目资金", prefix + "734万欧元。", 2803L
        )), List.of(source))).isInstanceOf(DailyBriefAiValidationException.class)
                .hasMessage("AI event monetary amounts must match its supporting Articles");
    }

    @Test
    void doesNotTreatPhysicalUnitsOrDatesAsCurrencies() {
        DailyBriefAiResult output = output(event("2028年投产", "产能10 GWh，面积250平方米。", 713L));
        assertThat(validator.validate(output, List.of(article(713L, "工厂新闻", null)))).isEqualTo(output);
    }

    private DailyBriefAiArticle article(long id, String title, String description) {
        return new DailyBriefAiArticle(id, title, description, "任意来源", null, null, List.of("任意主题"));
    }

    private DailyBriefAiEvent event(String title, String summary, long id) {
        return new DailyBriefAiEvent(title, summary, "值得关注，仍需进一步披露。", List.of(id));
    }

    private DailyBriefAiResult output(DailyBriefAiEvent event) {
        return new DailyBriefAiResult("项目动态", "今日项目进展。", List.of(event));
    }
}
