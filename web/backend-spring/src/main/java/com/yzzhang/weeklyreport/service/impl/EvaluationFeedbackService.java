package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackCandidateProvider;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackMessageFormatter;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackRunStore;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackRunStore.RunState;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackSnapshot;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackSnapshot.EmployeeFeedback;
import com.yzzhang.weeklyreport.service.notification.DingTalkWorkNoticeClient;
import com.yzzhang.weeklyreport.service.notification.DingTalkWorkNoticeClient.PersonalNotice;
import com.yzzhang.weeklyreport.service.notification.FeedbackRecipientResolver;
import com.yzzhang.weeklyreport.service.notification.WorkNoticeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class EvaluationFeedbackService {
    static final String PHASE_EMPLOYEE_SENDING = "EMPLOYEE_SENDING";
    static final String PHASE_UNKNOWN = "UNKNOWN";
    static final String PHASE_COMPLETE = "COMPLETE";

    private static final Logger log = LoggerFactory.getLogger(EvaluationFeedbackService.class);
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");

    private final EvaluationFeedbackCandidateProvider candidateProvider;
    private final EvaluationFeedbackRunStore runStore;
    private final DingTalkWorkNoticeClient workNoticeClient;
    private final FeedbackRecipientResolver recipientResolver;
    private final EvaluationFeedbackMessageFormatter messageFormatter;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public EvaluationFeedbackService(
        EvaluationFeedbackCandidateProvider candidateProvider,
        EvaluationFeedbackRunStore runStore,
        DingTalkWorkNoticeClient workNoticeClient,
        FeedbackRecipientResolver recipientResolver,
        EvaluationFeedbackMessageFormatter messageFormatter
    ) {
        this(
            candidateProvider,
            runStore,
            workNoticeClient,
            recipientResolver,
            messageFormatter,
            Clock.system(CN_ZONE)
        );
    }

    EvaluationFeedbackService(
        EvaluationFeedbackCandidateProvider candidateProvider,
        EvaluationFeedbackRunStore runStore,
        DingTalkWorkNoticeClient workNoticeClient,
        FeedbackRecipientResolver recipientResolver,
        EvaluationFeedbackMessageFormatter messageFormatter,
        Clock clock
    ) {
        this.candidateProvider = candidateProvider;
        this.runStore = runStore;
        this.workNoticeClient = workNoticeClient;
        this.recipientResolver = recipientResolver;
        this.messageFormatter = messageFormatter;
        this.clock = clock;
    }

    public void runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.info("Evaluation feedback skipped because another run is active.");
            return;
        }
        FeedbackRecipientResolver.Recipient administrator = resolveAdministrator();
        if (administrator == null) {
            running.set(false);
            return;
        }

        String weekLabel = previousWeekLabel();
        boolean administratorNotified = false;
        try {
            Optional<RunState> previous = runStore.load(weekLabel);
            if (previous.filter(state -> PHASE_COMPLETE.equals(state.phase())).isPresent()) {
                log.info("Evaluation feedback already completed for {}.", weekLabel);
                return;
            }
            if (previous.filter(state -> PHASE_EMPLOYEE_SENDING.equals(state.phase())
                && state.sentCount() == state.eligibleCount()).isPresent()) {
                sendAdministratorSuccess(administrator, previous.get());
                runStore.save(withPhase(previous.get(), PHASE_COMPLETE));
                return;
            }
            if (previous.filter(state -> PHASE_EMPLOYEE_SENDING.equals(state.phase())
                || PHASE_UNKNOWN.equals(state.phase())).isPresent()) {
                administratorNotified = sendAdministratorFailure(
                    administrator,
                    weekLabel,
                    "上次员工评价通知发送结果不确定，系统未自动重发"
                );
                return;
            }

            EvaluationFeedbackSnapshot snapshot = candidateProvider.collect(weekLabel);
            int eligible = snapshot.employees().size();
            String feedbackDigest = messageFormatter.digest(weekLabel, snapshot.employees());

            AtomicInteger sent = new AtomicInteger();
            runStore.save(runState(weekLabel, PHASE_EMPLOYEE_SENDING, eligible, 0, feedbackDigest));
            if (eligible > 0) {
                List<PersonalNotice> notices = snapshot.employees().stream()
                    .map(employee -> new PersonalNotice(
                        "周报个人评价",
                        messageFormatter.format(weekLabel, employee),
                        employee.userId()
                    ))
                    .toList();
                try {
                    workNoticeClient.sendPersonalMarkdown(notices, delivered -> {
                        sent.set(delivered);
                        runStore.save(runState(
                            weekLabel,
                            PHASE_EMPLOYEE_SENDING,
                            eligible,
                            delivered,
                            feedbackDigest
                        ));
                    });
                } catch (WorkNoticeException ex) {
                    runStore.save(runState(
                        weekLabel,
                        PHASE_UNKNOWN,
                        eligible,
                        sent.get(),
                        feedbackDigest
                    ));
                    administratorNotified = sendAdministratorFailure(
                        administrator,
                        weekLabel,
                        "员工通知远程结果不确定"
                    );
                    return;
                }
            }
            RunState sentState = runState(
                weekLabel,
                PHASE_EMPLOYEE_SENDING,
                eligible,
                sent.get(),
                feedbackDigest
            );
            sendAdministratorSuccess(administrator, sentState);
            administratorNotified = true;
            runStore.save(withPhase(sentState, PHASE_COMPLETE));
        } catch (RuntimeException ex) {
            if (!administratorNotified) {
                administratorNotified = sendAdministratorFailure(
                    administrator,
                    weekLabel,
                    "正式评价未就绪或校验失败"
                );
            }
            log.warn("Evaluation feedback failed; administratorNotified={}", administratorNotified);
        } finally {
            running.set(false);
        }
    }

    private FeedbackRecipientResolver.Recipient resolveAdministrator() {
        try {
            FeedbackRecipientResolver.Recipient recipient = recipientResolver.resolve();
            if (!recipient.userIds().isEmpty()) {
                return recipient;
            }
        } catch (RuntimeException ignored) {
            // The log deliberately omits configuration values and recipient details.
        }
        log.error("Evaluation feedback aborted: administrator recipient is unavailable.");
        return null;
    }

    private void sendAdministratorSuccess(
        FeedbackRecipientResolver.Recipient administrator,
        RunState state
    ) {
        String markdown = """
            ### 周报个人评价通知：成功

            - **业务周**：%s
            - **任务执行**：成功
            - **符合发送条件人数**：%d
            - **已发送人数**：%d

            本结果不包含员工姓名、userid 或评价正文。
            """.formatted(state.weekLabel(), state.eligibleCount(), state.sentCount()).strip();
        workNoticeClient.sendMarkdown("周报个人评价通知：成功", markdown, administrator.userIds());
    }

    private boolean sendAdministratorFailure(
        FeedbackRecipientResolver.Recipient administrator,
        String weekLabel,
        String reason
    ) {
        String markdown = """
            ### 周报个人评价通知：失败

            - **业务周**：%s
            - **任务执行**：失败
            - **安全原因**：%s

            请检查正式评价状态、反馈清单和钉钉应用配置。本结果不包含员工姓名、userid 或评价正文。
            """.formatted(weekLabel, reason).strip();
        try {
            workNoticeClient.sendMarkdown("周报个人评价通知：失败", markdown, administrator.userIds());
            return true;
        } catch (RuntimeException ignored) {
            log.error("Evaluation feedback administrator failure notification could not be delivered.");
            return false;
        }
    }

    private RunState withPhase(RunState state, String phase) {
        return runState(
            state.weekLabel(),
            phase,
            state.eligibleCount(),
            state.sentCount(),
            state.feedbackDigest()
        );
    }

    private RunState runState(
        String weekLabel,
        String phase,
        int eligibleCount,
        int sentCount,
        String feedbackDigest
    ) {
        if (feedbackDigest == null || feedbackDigest.isBlank()) {
            return runStore.state(weekLabel, phase, eligibleCount, sentCount);
        }
        return runStore.state(weekLabel, phase, eligibleCount, sentCount, feedbackDigest);
    }

    private String previousWeekLabel() {
        LocalDate start = LocalDate.now(clock.withZone(CN_ZONE))
            .minusWeeks(1)
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        java.time.temporal.WeekFields iso = java.time.temporal.WeekFields.ISO;
        return "%d-W%02d".formatted(start.get(iso.weekBasedYear()), start.get(iso.weekOfWeekBasedYear()));
    }
}
