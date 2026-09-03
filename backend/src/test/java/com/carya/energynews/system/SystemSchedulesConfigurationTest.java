package com.carya.energynews.system;

import com.carya.energynews.dailybrief.DailyBriefSchedulerProperties;
import com.carya.energynews.watchlistdiscovery.WatchlistDiscoverySchedulerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemSchedulesConfigurationTest {

    // Bind real application defaults without starting any scheduler, database, or provider.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ScheduleConfiguration.class)
            .withInitializer(context -> {
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                try {
                    new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                            .forEach(sources::addLast);
                } catch (IOException exception) {
                    throw new AssertionError("Cannot load application defaults", exception);
                }
            });

    @Test
    void exposesMorningDefaultsWithoutChangingEnabledOrOtherSchedulerSettings() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SystemSchedulesResponse response = context.getBean(SystemSchedulesController.class).getSchedules();
            assertThat(response.newsDiscovery())
                    .isEqualTo(new ScheduleResponse(false, "0 0 8 * * *", "Asia/Shanghai", "08:00"));
            assertThat(response.dailyBrief())
                    .isEqualTo(new ScheduleResponse(false, "0 10 8 * * *", "Asia/Shanghai", "08:10"));

            var discovery = context.getBean(WatchlistDiscoverySchedulerProperties.class);
            assertThat(discovery.lookbackHours()).isEqualTo(36);
            assertThat(discovery.limitPerKeyword()).isEqualTo(5);
            assertThat(discovery.delayBetweenKeywordsMs()).isEqualTo(10_000);
            assertThat(discovery.maxRequestsPerRun()).isEqualTo(20);
            assertThat(context.getBean(DailyBriefSchedulerProperties.class).dayOffset()).isEqualTo(-1);
            assertThat(context.getEnvironment().getProperty("app.news-sync.cron"))
                    .isEqualTo("0 0 20 * * *");
        });
    }

    @Test
    void reportsResolvedEnvironmentOverridesInsteadOfDefaultValues() {
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources()
                .addFirst(new SystemEnvironmentPropertySource("schedule-test-env", Map.of(
                        "NEWS_DISCOVERY_SCHEDULER_ENABLED", "true",
                        "NEWS_DISCOVERY_SCHEDULER_CRON", "0 7 6 * * *",
                        "NEWS_DISCOVERY_SCHEDULER_ZONE", "Europe/Berlin",
                        "DAILY_BRIEF_SCHEDULER_ENABLED", "true",
                        "DAILY_BRIEF_SCHEDULER_CRON", "0 42 23 * * *",
                        "DAILY_BRIEF_SCHEDULER_ZONE", "UTC"
                )))).run(context -> {
                    assertThat(context).hasNotFailed();
                    SystemSchedulesResponse response = context.getBean(SystemSchedulesController.class).getSchedules();
                    assertThat(response.newsDiscovery())
                            .isEqualTo(new ScheduleResponse(true, "0 7 6 * * *", "Europe/Berlin", "06:07"));
                    assertThat(response.dailyBrief())
                            .isEqualTo(new ScheduleResponse(true, "0 42 23 * * *", "UTC", "23:42"));
                });
    }

    @Test
    void directConfigurationOverridesPreserveIndependentEnabledStatesAndUnsupportedCron() {
        contextRunner.withPropertyValues(
                "app.discovery.scheduler.enabled=false",
                "app.discovery.scheduler.cron=0 */10 * * * *",
                "app.daily-brief.scheduler.enabled=true",
                "app.daily-brief.scheduler.cron=0 0 8 * * MON"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            SystemSchedulesResponse response = context.getBean(SystemSchedulesController.class).getSchedules();
            assertThat(response.newsDiscovery())
                    .isEqualTo(new ScheduleResponse(false, "0 */10 * * * *", "Asia/Shanghai", null));
            assertThat(response.dailyBrief())
                    .isEqualTo(new ScheduleResponse(true, "0 0 8 * * MON", "Asia/Shanghai", null));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({WatchlistDiscoverySchedulerProperties.class, DailyBriefSchedulerProperties.class})
    @Import(SystemSchedulesController.class)
    static class ScheduleConfiguration {
    }
}
