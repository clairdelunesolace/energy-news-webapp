package com.carya.energynews.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NewsDiscoveryProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    NewsDiscoveryConfiguration.class,
                    BraveNewsDiscoveryConfiguration.class,
                    GNewsDiscoveryConfiguration.class
            )
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void gnewsDefaultsToTenResultsPerRequest() {
        GNewsDiscoveryProperties properties = new GNewsDiscoveryProperties("test-key", null);

        assertThat(properties.maxResultsPerRequest()).isEqualTo(10);
    }

    @Test
    void noneSelectsNoProviderEvenWhenBothKeysExist() {
        contextRunner
                .withPropertyValues(
                        "app.discovery.provider=none",
                        "app.discovery.brave.api-key=brave-test-key",
                        "app.discovery.gnews.api-key=gnews-test-key"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(NewsDiscoveryProvider.class);
                    assertThat(context).doesNotHaveBean(NewsDiscoveryService.class);
                });
    }

    @Test
    void selectedGNewsRequiresApiKey() {
        contextRunner
                .withPropertyValues("app.discovery.provider=gnews")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "GNEWS_API_KEY must be configured and must not be blank when GNews discovery is selected"
                            );
                });
    }

    @Test
    void selectedBraveRequiresApiKey() {
        contextRunner
                .withPropertyValues("app.discovery.provider=brave")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "BRAVE_SEARCH_API_KEY must be configured and must not be blank when Brave discovery is selected"
                            );
                });
    }

    @Test
    void gnewsSelectsOnlyGNewsWhenBothKeysExist() {
        contextRunner
                .withPropertyValues(
                        "app.discovery.provider=gnews",
                        "app.discovery.brave.api-key=brave-test-key",
                        "app.discovery.gnews.api-key=gnews-test-key"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NewsDiscoveryProvider.class);
                    assertThat(context.getBean(NewsDiscoveryProvider.class))
                            .isInstanceOf(GNewsNewsDiscoveryProvider.class);
                    assertThat(context).hasSingleBean(NewsDiscoveryService.class);
                    assertThat(context.getBean(NewsDiscoveryService.class).providerName())
                            .isEqualTo("gnews");
                });
    }

    @Test
    void braveSelectsOnlyBraveWhenBothKeysExist() {
        contextRunner
                .withPropertyValues(
                        "app.discovery.provider=brave",
                        "app.discovery.brave.api-key=brave-test-key",
                        "app.discovery.gnews.api-key=gnews-test-key"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NewsDiscoveryProvider.class);
                    assertThat(context.getBean(NewsDiscoveryProvider.class))
                            .isInstanceOf(BraveNewsDiscoveryProvider.class);
                    assertThat(context).hasSingleBean(NewsDiscoveryService.class);
                    assertThat(context.getBean(NewsDiscoveryService.class).providerName())
                            .isEqualTo("brave-news");
                });
    }

    @Test
    void rejectsUnsupportedProvider() {
        contextRunner
                .withPropertyValues("app.discovery.provider=automatic")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "NEWS_DISCOVERY_PROVIDER must be one of: none, brave, gnews"
                            );
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void rejectsInvalidGNewsMaxResultsPerRequest(int maxResultsPerRequest) {
        contextRunner
                .withPropertyValues(
                        "app.discovery.provider=gnews",
                        "app.discovery.gnews.api-key=gnews-test-key",
                        "app.discovery.gnews.max-results-per-request=" + maxResultsPerRequest
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "GNEWS_MAX_RESULTS_PER_REQUEST must be between 1 and 100"
                            );
                });
    }

}
