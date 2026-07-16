package com.yzzhang.weeklyreport.config;

import com.yzzhang.weeklyreport.service.impl.EvaluationFeedbackService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EvaluationFeedbackSchedulerTest {
    @Test
    void enabledSchedulerRequiresAConfiguredHrContact() {
        EvaluationFeedbackProperties properties = new EvaluationFeedbackProperties();

        assertThatThrownBy(() -> new EvaluationFeedbackScheduler(
            mock(EvaluationFeedbackService.class),
            properties
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("WEEKLY_EVALUATION_FEEDBACK_HR_CONTACT");
    }

    @Test
    void schedulerDelegatesWithoutSendingDirectly() {
        EvaluationFeedbackProperties properties = new EvaluationFeedbackProperties();
        properties.setHrContactName("示例HR联系人");
        EvaluationFeedbackService service = mock(EvaluationFeedbackService.class);
        EvaluationFeedbackScheduler scheduler = new EvaluationFeedbackScheduler(service, properties);

        scheduler.sendEvaluationFeedback();

        verify(service).runOnce();
    }
}
