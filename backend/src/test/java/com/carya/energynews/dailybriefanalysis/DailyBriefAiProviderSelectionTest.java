package com.carya.energynews.dailybriefanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DailyBriefAiProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    DailyBriefAiConfiguration.class,
                    GroqDailyBriefAiConfiguration.class
            )
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void defaultsToNoneAndDefaultModel() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DailyBriefAiProvider.class);
            assertThat(context.getBean(DailyBriefAiProperties.class).provider()).isEqualTo("none");
            assertThat(context.getBean(DailyBriefAiProperties.class).model())
                    .isEqualTo("openai/gpt-oss-20b");
        });
    }

    @Test
    void selectedGroqRequiresApiKey() {
        contextRunner
                .withPropertyValues("app.daily-brief.ai.provider=groq")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "GROQ_API_KEY must be configured and must not be blank when Groq daily brief AI is selected"
                    );
                });
    }

    @Test
    void selectedGroqUsesConfiguredModel() {
        contextRunner
                .withPropertyValues(
                        "app.daily-brief.ai.provider=groq",
                        "app.daily-brief.ai.model=custom/model",
                        "app.daily-brief.ai.groq.api-key=test-key"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DailyBriefAiProvider.class);
                    DailyBriefAiProvider provider = context.getBean(DailyBriefAiProvider.class);
                    assertThat(provider.providerName()).isEqualTo("groq");
                    assertThat(provider.model()).isEqualTo("custom/model");
                });
    }

    @Test
    void rejectsUnsupportedProviderAndBlankModel() {
        contextRunner
                .withPropertyValues("app.daily-brief.ai.provider=openai")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "DAILY_BRIEF_AI_PROVIDER must be one of: none, groq"
                    );
                });

        contextRunner
                .withPropertyValues("app.daily-brief.ai.model=  ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "DAILY_BRIEF_AI_MODEL must not be blank"
                    );
                });
    }
}
