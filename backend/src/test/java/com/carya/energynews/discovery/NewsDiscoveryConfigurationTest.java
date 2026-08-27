package com.carya.energynews.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsDiscoveryConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NewsDiscoveryConfiguration.class);

    @Test
    void doesNotCreateServiceWithoutProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(NewsDiscoveryService.class);
        });
    }

    @Test
    void createsServiceWhenProviderIsConfigured() {
        contextRunner
                .withBean(NewsDiscoveryProvider.class, EmptyProvider::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NewsDiscoveryProvider.class);
                    assertThat(context).hasSingleBean(NewsDiscoveryService.class);
                });
    }

    private static final class EmptyProvider implements NewsDiscoveryProvider {

        @Override
        public String providerName() {
            return "empty-test-provider";
        }

        @Override
        public List<DiscoveredArticle> discover(NewsDiscoveryQuery query) {
            return List.of();
        }
    }
}
