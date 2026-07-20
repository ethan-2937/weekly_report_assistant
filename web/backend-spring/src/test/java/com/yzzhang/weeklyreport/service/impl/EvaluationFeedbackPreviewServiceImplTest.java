package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.config.EvaluationFeedbackProperties;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackCandidateProvider;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackException;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackMessageFormatter;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackPreviewException;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackRunStore;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackRunStore.RunState;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackSnapshot;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackSnapshot.EmployeeFeedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationFeedbackPreviewServiceImplTest {
    private static final String WEEK = "2026-W29";

    private EvaluationFeedbackRunStore runStore;
    private EvaluationFeedbackCandidateProvider candidateProvider;
    private EvaluationFeedbackMessageFormatter messageFormatter;
    private EvaluationFeedbackPreviewServiceImpl service;

    @BeforeEach
    void setUp() {
        runStore = mock(EvaluationFeedbackRunStore.class);
        candidateProvider = mock(EvaluationFeedbackCandidateProvider.class);
        EvaluationFeedbackProperties properties = new EvaluationFeedbackProperties();
        properties.setHrContactName("示例HR联系人");
        messageFormatter = new EvaluationFeedbackMessageFormatter(properties);
        service = new EvaluationFeedbackPreviewServiceImpl(
            runStore,
            candidateProvider,
            messageFormatter
        );
    }

    @Test
    void returnsExactRenderedMessagesWithoutInternalIdentifiers() {
        EvaluationFeedbackSnapshot snapshot = snapshot(
            "feedback-digest",
            Instant.parse("2026-07-20T03:59:00Z")
        );
        when(runStore.load(WEEK)).thenReturn(Optional.of(completeState(
            messageFormatter.digest(WEEK, snapshot.employees())
        )));
        when(candidateProvider.collect(WEEK)).thenReturn(snapshot);

        var preview = service.getPreview(WEEK);

        assertThat(preview.exactMatch()).isTrue();
        assertThat(preview.verificationMode()).isEqualTo("DIGEST");
        assertThat(preview.warning()).isEmpty();
        assertThat(preview.notifications()).singleElement().satisfies(notification -> {
            assertThat(notification.name()).isEqualTo("示例员工甲");
            assertThat(notification.department()).isEqualTo("虚构研发部");
            assertThat(notification.title()).isEqualTo("工程师");
            assertThat(notification.markdown())
                .contains("### 示例员工甲，您的 2026-W29 周报评价")
                .contains("感谢您本周形成了明确交付物")
                .contains("如有疑问请联系HR示例HR联系人")
                .doesNotContain("test-user-001");
        });
    }

    @Test
    void supportsLegacyCompletionStateOnlyWhenArtifactPredatesCompletion() {
        when(runStore.load(WEEK)).thenReturn(Optional.of(completeState(null)));
        when(candidateProvider.collect(WEEK)).thenReturn(snapshot(
            "current-digest",
            Instant.parse("2026-07-20T03:59:59Z")
        ));

        var legacy = service.getPreview(WEEK);
        assertThat(legacy.exactMatch()).isTrue();
        assertThat(legacy.verificationMode()).isEqualTo("LEGACY_TIME");
        assertThat(legacy.warning()).contains("旧版发送状态");

        when(candidateProvider.collect(WEEK)).thenReturn(snapshot(
            "regenerated-digest",
            Instant.parse("2026-07-20T04:00:01Z")
        ));
        var changed = service.getPreview(WEEK);
        assertThat(changed.exactMatch()).isFalse();
        assertThat(changed.notifications()).isEmpty();
        assertThat(changed.warning()).contains("发生变化");
    }

    @Test
    void rejectsIncompleteMismatchedOrUnsafeSourcesWithoutLeakingDetails() {
        when(runStore.load(WEEK)).thenReturn(Optional.of(
            new RunState(WEEK, "EMPLOYEE_SENDING", 2, 1, "2026-07-20T04:00:00Z")
        ));
        assertThatThrownBy(() -> service.getPreview(WEEK))
            .isInstanceOf(EvaluationFeedbackPreviewException.class)
            .extracting(error -> ((EvaluationFeedbackPreviewException) error).reason())
            .isEqualTo(EvaluationFeedbackPreviewException.Reason.NOT_COMPLETE);

        when(runStore.load(WEEK)).thenReturn(Optional.of(completeState("different-rendered-message-digest")));
        when(candidateProvider.collect(WEEK))
            .thenThrow(new EvaluationFeedbackException("fictional-token 虚构评价正文"));
        assertThatThrownBy(() -> service.getPreview(WEEK))
            .isInstanceOf(EvaluationFeedbackPreviewException.class)
            .hasMessage("CONTENT_UNAVAILABLE")
            .hasMessageNotContaining("fictional-token")
            .hasMessageNotContaining("虚构评价正文");
    }

    @Test
    void rejectsInvalidWeekAndEligibleCountMismatch() {
        assertThatThrownBy(() -> service.getPreview("../../2026-W29"))
            .isInstanceOf(IllegalArgumentException.class);

        when(runStore.load(WEEK)).thenReturn(Optional.of(
            new RunState(WEEK, "COMPLETE", 2, 2, "2026-07-20T04:00:00Z", "feedback-digest")
        ));
        when(candidateProvider.collect(WEEK)).thenReturn(snapshot(
            "feedback-digest",
            Instant.parse("2026-07-20T03:59:00Z")
        ));
        assertThatThrownBy(() -> service.getPreview(WEEK))
            .isInstanceOf(EvaluationFeedbackPreviewException.class)
            .hasMessage("CONTENT_UNAVAILABLE");
    }

    private RunState completeState(String digest) {
        return new RunState(WEEK, "COMPLETE", 1, 1, "2026-07-20T04:00:00Z", digest);
    }

    private EvaluationFeedbackSnapshot snapshot(String digest, Instant updatedAt) {
        return new EvaluationFeedbackSnapshot(WEEK, List.of(
            new EmployeeFeedback(
                "test-user-001",
                "示例员工甲",
                "虚构研发部",
                "工程师",
                "本周形成了明确的虚构交付物。",
                "建议补充量化效果和明确日期。",
                "感谢您本周形成了明确交付物。团队因您的认真投入而更加稳健，也更有力量。"
            )
        ), digest, updatedAt);
    }
}
