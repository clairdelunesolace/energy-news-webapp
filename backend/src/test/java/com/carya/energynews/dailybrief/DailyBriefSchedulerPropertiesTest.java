package com.carya.energynews.dailybrief;

import com.carya.energynews.dailybriefanalysis.DailyBriefAnalysisService;
import com.carya.energynews.watchlist.WatchlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.io.IOException;
import java.time.Clock;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

class DailyBriefSchedulerPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulerConfiguration.class)
            .withBean(WatchlistRepository.class, () -> mock(WatchlistRepository.class))
            .withBean(DailyBriefService.class, () -> mock(DailyBriefService.class))
            .withBean(DailyBriefAnalysisService.class, () -> mock(DailyBriefAnalysisService.class))
            .withBean(Clock.class, Clock::systemUTC)
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
    void defaultsToDisabledWithMorningPreviousDayShanghaiAndNoScheduledTask() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(DailyBriefScheduler.class);
            DailyBriefSchedulerProperties properties = context.getBean(DailyBriefSchedulerProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.cron()).isEqualTo("0 40 10 * * *");
            assertThat(properties.zoneId()).isEqualTo(ZoneId.of("Asia/Shanghai"));
            assertThat(properties.dayOffset()).isEqualTo(-1);
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
        });
    }

    @Test
    void environmentOverridesBindAndRegisterDedicatedScheduler() throws Exception {
        Scheduled scheduled = DailyBriefScheduler.class.getMethod("triggerScheduledGeneration")
                .getAnnotation(Scheduled.class);
        contextRunner.withPropertyValues("DAILY_BRIEF_SCHEDULER_ENABLED=true",
                "DAILY_BRIEF_SCHEDULER_CRON=0 15 4 1 1 *", "DAILY_BRIEF_SCHEDULER_ZONE=UTC",
                "DAILY_BRIEF_SCHEDULER_DAY_OFFSET=0")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DailyBriefScheduler.class);
                    DailyBriefSchedulerProperties properties = context.getBean(DailyBriefSchedulerProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.cron()).isEqualTo("0 15 4 1 1 *");
                    assertThat(properties.zone()).isEqualTo("UTC");
                    assertThat(properties.dayOffset()).isZero();
                    assertThat(context.getEnvironment().resolveRequiredPlaceholders(scheduled.cron()))
                            .isEqualTo(properties.cron());
                    assertThat(context.getEnvironment().resolveRequiredPlaceholders(scheduled.zone()))
                            .isEqualTo(properties.zone());
                    assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).hasSize(1);
                });
    }

    @Test
    void explicitFalseDoesNotRegisterScheduler() {
        contextRunner.withPropertyValues("app.daily-brief.scheduler.enabled=false").run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(DailyBriefScheduler.class));
    }

    @Test
    void rejectsMissingOrInvalidCronAndZone() {
        for (String cron : new String[]{null, "", "invalid cron"}) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new DailyBriefSchedulerProperties(false, cron, "Asia/Shanghai", -1));
        }
        for (String zone : new String[]{null, "", "Not/AZone"}) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new DailyBriefSchedulerProperties(false, "0 40 10 * * *", zone, -1));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @EnableConfigurationProperties(DailyBriefSchedulerProperties.class)
    @Import(DailyBriefScheduler.class)
    static class SchedulerConfiguration {
    }
}
