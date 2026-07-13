package com.yzzhang.weeklyreport.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeekLabelUtilsTest {
    @Test
    void validatesCanonicalWeekLabels() {
        assertThat(WeekLabelUtils.isValid("2026-W28")).isTrue();
        assertThat(WeekLabelUtils.isValid("2026-W8")).isFalse();
        assertThat(WeekLabelUtils.isValid("week-28")).isFalse();
        assertThat(WeekLabelUtils.isValid(null)).isFalse();
    }

    @Test
    void returnsTheLastWeekLabelFromMixedText() {
        assertThat(WeekLabelUtils.lastWeekLabelIn("from 2026-W27 to 2026-W28"))
                .contains("2026-W28");
        assertThat(WeekLabelUtils.lastWeekLabelIn("no week here")).isEmpty();
    }
}
