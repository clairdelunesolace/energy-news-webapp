package com.carya.energynews.dailybriefanalysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyBriefAiEvidenceGuardFlagTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DailyBriefAiConfiguration.class, DailyBriefAiResultValidator.class);
    private final List<DailyBriefAiArticle> evidence = List.of(new DailyBriefAiArticle(
            712L, "Orion reportedly plans to acquire Blue Harbor", "$2.5 billion proposed price",
            "Publisher", null, null, List.of()
    ));

    @Test
    void evidenceGuardDefaultsToEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DailyBriefAiProperties.class).evidenceGuardEnabled()).isTrue();
            assertThatThrownBy(() -> context.getBean(DailyBriefAiResultValidator.class)
                    .validate(fidelityViolations().getFirst(), evidence))
                    .isInstanceOf(DailyBriefAiValidationException.class);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void togglesOnlyFidelityChecks(boolean enabled) {
        contextRunner.withPropertyValues("app.daily-brief.ai.evidence-guard-enabled=" + enabled)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(DailyBriefAiProperties.class).evidenceGuardEnabled()).isEqualTo(enabled);
                    DailyBriefAiResultValidator validator = context.getBean(DailyBriefAiResultValidator.class);
                    for (DailyBriefAiResult result : fidelityViolations()) {
                        if (enabled) {
                            assertThatThrownBy(() -> validator.validate(result, evidence))
                                    .isInstanceOf(DailyBriefAiValidationException.class);
                        } else {
                            assertThat(validator.validate(result, evidence)).isEqualTo(result);
                        }
                    }
                });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void coreStructureAndSnapshotMembershipAlwaysRemainEnforced(boolean enabled) {
        contextRunner.withPropertyValues("app.daily-brief.ai.evidence-guard-enabled=" + enabled)
                .run(context -> {
                    DailyBriefAiResultValidator validator = context.getBean(DailyBriefAiResultValidator.class);
                    DailyBriefAiEvent valid = event("据报道拟收购Blue Harbor", "据报道，交易仍待确认。", List.of(712L));
                    List<DailyBriefAiResult> invalidResults = List.of(
                            new DailyBriefAiResult(" ", "概览", List.of(valid)),
                            new DailyBriefAiResult("标题", " ", List.of(valid)),
                            new DailyBriefAiResult("标题", "概览", null),
                            new DailyBriefAiResult("标题", "概览", List.of()),
                            new DailyBriefAiResult("标题", "概览", IntStream.range(0, 6).mapToObj(i -> valid).toList()),
                            result(event(" ", valid.summary(), List.of(712L))),
                            result(event(valid.title(), " ", List.of(712L))),
                            result(new DailyBriefAiEvent(valid.title(), valid.summary(), " ", List.of(712L))),
                            result(event(valid.title(), valid.summary(), List.of())),
                            result(event(valid.title(), valid.summary(), null)),
                            result(event(valid.title(), valid.summary(), List.of(999_999L)))
                    );
                    for (DailyBriefAiResult result : invalidResults) {
                        assertThatThrownBy(() -> validator.validate(result, evidence))
                                .isInstanceOf(DailyBriefAiValidationException.class);
                    }
                    assertThatThrownBy(() -> validator.validate(null, evidence))
                            .isInstanceOf(DailyBriefAiValidationException.class);
                    assertThat(validator.validate(result(valid), evidence)).isEqualTo(result(valid));
                });
    }

    private List<DailyBriefAiResult> fidelityViolations() {
        DailyBriefAiEvent qualified = event("据报道拟收购Blue Harbor", "据报道，交易仍待确认。", List.of(712L));
        return List.of(
                result(event("已收购Blue Harbor", "交易已完成。", List.of(712L))),
                new DailyBriefAiResult("Blue Harbor已被收购", "概览", List.of(qualified)),
                new DailyBriefAiResult("标题", "Blue Harbor已被收购。", List.of(qualified)),
                result(event(qualified.title(), "据报道，交易价格为2.5亿美元。", List.of(712L)))
        );
    }

    private DailyBriefAiEvent event(String title, String summary, List<Long> ids) {
        return new DailyBriefAiEvent(title, summary, "如果交易完成，可能扩大业务范围。", ids);
    }

    private DailyBriefAiResult result(DailyBriefAiEvent event) {
        return new DailyBriefAiResult("行业动态", "今日概览。", List.of(event));
    }
}
