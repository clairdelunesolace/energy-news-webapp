package com.carya.energynews.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NewsDiscoveryConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NewsDiscoveryConfiguration.class);

    @Test
    void defaultsToNoSelectedProviderOrService() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(NewsDiscoveryProperties.class);
            assertThat(context.getBean(NewsDiscoveryProperties.class).provider())
                    .isEqualTo("none");
            assertThat(context).doesNotHaveBean(NewsDiscoveryService.class);
        });
    }
}
