package com.carya.energynews.dailybrief;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DailyBriefPropertiesTest {

    @Test
    void acceptsValidZoneAndLimit() {
        DailyBriefProperties properties = new DailyBriefProperties("Asia/Shanghai", 10);

        assertThat(properties.zoneId()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThat(properties.maxItems()).isEqualTo(10);
    }

    @Test
    void rejectsInvalidZoneAndLimit() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DailyBriefProperties("not-a-zone", 10))
                .withMessage("Daily brief zone must be a valid time-zone ID");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DailyBriefProperties("UTC", 0))
                .withMessage("Daily brief max items must be between 1 and 20");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DailyBriefProperties("UTC", 21))
                .withMessage("Daily brief max items must be between 1 and 20");
    }
}
