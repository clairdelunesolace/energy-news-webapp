package com.carya.energynews.system;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleResponseTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0 0 8 * * *|08:00",
            "0 10 8 * * *|08:10",
            "0 0 0 * * *|00:00",
            "59 59 23 * * *|23:59",
            "17 5 6 * * *|06:05",
            "0   7   9 * * *|09:07"
    })
    void formatsOnlySingleDailyTimesAtMinutePrecision(String cron, String expectedTime) {
        ScheduleResponse response = ScheduleResponse.from(true, cron, "UTC");

        assertThat(response.dailyTime()).isEqualTo(expectedTime);
        assertThat(response.cron()).isEqualTo(cron);
        assertThat(response.zone()).isEqualTo("UTC");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0 */10 * * * *", "0 0 8,20 * * *", "0 0 8-10 * * *",
            "0 0 8 * * MON-FRI", "0 0 8 1 * *", "0 0 8 * JAN *",
            "0 0 8 ? * *", "@daily", "* 0 8 * * *", "0/15 0 8 * * *",
            "60 0 8 * * *", "0 60 8 * * *", "0 0 24 * * *"
    })
    void doesNotGuessDailyTimesForUnsupportedExpressions(String cron) {
        ScheduleResponse response = ScheduleResponse.from(true, cron, "Asia/Shanghai");

        assertThat(response.dailyTime()).isNull();
        assertThat(response.cron()).isEqualTo(cron);
    }
}
