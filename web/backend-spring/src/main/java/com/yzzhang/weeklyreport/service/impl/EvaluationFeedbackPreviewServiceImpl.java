package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.service.EvaluationFeedbackPreviewService;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackCandidateProvider;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackException;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackMessageFormatter;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackPreviewException;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackPreviewException.Reason;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackRunStore;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackRunStore.RunState;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackSnapshot;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackSnapshot.EmployeeFeedback;
import com.yzzhang.weeklyreport.util.WeekLabelUtils;
import com.yzzhang.weeklyreport.vo.EvaluationFeedbackPreviewVO;
import com.yzzhang.weeklyreport.vo.EvaluationFeedbackPreviewVO.NotificationVO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class EvaluationFeedbackPreviewServiceImpl implements EvaluationFeedbackPreviewService {
    private static final String COMPLETE = "COMPLETE";
    private static final int MAX_ITEMS = 300;
    private static final int MAX_TOTAL_MARKDOWN_CHARS = 500_000;
    private static final String CHANGED_WARNING =
        "反馈内容在通知完成后发生变化，无法确认当前内容与当时发送内容一致。";
    private static final String LEGACY_WARNING =
        "该周使用旧版发送状态，正文按反馈文件时间重建，无法校验发送后的姓名或 HR 联系方式变更。";

    private final EvaluationFeedbackRunStore runStore;
    private final EvaluationFeedbackCandidateProvider candidateProvider;
    private final EvaluationFeedbackMessageFormatter messageFormatter;

    public EvaluationFeedbackPreviewServiceImpl(
        EvaluationFeedbackRunStore runStore,
        EvaluationFeedbackCandidateProvider candidateProvider,
        EvaluationFeedbackMessageFormatter messageFormatter
    ) {
        this.runStore = runStore;
        this.candidateProvider = candidateProvider;
        this.messageFormatter = messageFormatter;
    }

    @Override
    public EvaluationFeedbackPreviewVO getPreview(String weekLabel) {
        if (!WeekLabelUtils.isValid(weekLabel)) {
            throw new IllegalArgumentException("invalid week label");
        }
        RunState state = runStore.load(weekLabel)
            .orElseThrow(() -> new EvaluationFeedbackPreviewException(Reason.NOT_COMPLETE));
        if (!COMPLETE.equals(state.phase()) || state.sentCount() != state.eligibleCount()) {
            throw new EvaluationFeedbackPreviewException(Reason.NOT_COMPLETE);
        }

        EvaluationFeedbackSnapshot snapshot;
        try {
            snapshot = candidateProvider.collect(weekLabel);
        } catch (EvaluationFeedbackException ex) {
            throw new EvaluationFeedbackPreviewException(Reason.CONTENT_UNAVAILABLE);
        }
        if (snapshot.employees().size() != state.eligibleCount() || snapshot.employees().size() > MAX_ITEMS) {
            throw new EvaluationFeedbackPreviewException(Reason.CONTENT_UNAVAILABLE);
        }

        Verification verification = verifySentContent(state, snapshot);
        List<NotificationVO> notifications = verification.matches()
            ? renderNotifications(weekLabel, snapshot.employees())
            : List.of();
        return new EvaluationFeedbackPreviewVO(
            weekLabel,
            state.phase(),
            state.eligibleCount(),
            state.sentCount(),
            state.updatedAt(),
            verification.matches(),
            verification.mode(),
            verification.warning(),
            notifications
        );
    }

    private Verification verifySentContent(RunState state, EvaluationFeedbackSnapshot snapshot) {
        if (hasText(state.feedbackDigest())) {
            boolean matches = state.feedbackDigest().equals(
                messageFormatter.digest(snapshot.weekLabel(), snapshot.employees())
            );
            return new Verification(matches, matches ? "DIGEST" : "MISMATCH", matches ? "" : CHANGED_WARNING);
        }
        try {
            Instant completedAt = Instant.parse(state.updatedAt());
            boolean matches = !snapshot.feedbackUpdatedAt().isAfter(completedAt);
            return new Verification(
                matches,
                matches ? "LEGACY_TIME" : "MISMATCH",
                matches ? LEGACY_WARNING : CHANGED_WARNING
            );
        } catch (DateTimeParseException | NullPointerException ex) {
            return new Verification(false, "MISMATCH", CHANGED_WARNING);
        }
    }

    private List<NotificationVO> renderNotifications(String weekLabel, List<EmployeeFeedback> employees) {
        List<NotificationVO> result = new ArrayList<>(employees.size());
        int totalChars = 0;
        for (EmployeeFeedback employee : employees) {
            String markdown = messageFormatter.format(weekLabel, employee);
            totalChars += markdown.length();
            if (totalChars > MAX_TOTAL_MARKDOWN_CHARS) {
                throw new EvaluationFeedbackPreviewException(Reason.CONTENT_UNAVAILABLE);
            }
            result.add(new NotificationVO(
                safeLabel(employee.name(), "", 80),
                safeLabel(employee.department(), "未填写部门", 120),
                safeLabel(employee.title(), "未填写职位", 120),
                markdown
            ));
        }
        return result;
    }

    private String safeLabel(String value, String fallback, int maxLength) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) return fallback;
        if (normalized.length() > maxLength || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new EvaluationFeedbackPreviewException(Reason.CONTENT_UNAVAILABLE);
        }
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Verification(boolean matches, String mode, String warning) {}
}
