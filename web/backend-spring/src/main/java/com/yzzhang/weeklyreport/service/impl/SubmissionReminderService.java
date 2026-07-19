package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.service.notification.DingTalkWorkNoticeClient;
import com.yzzhang.weeklyreport.service.notification.FeedbackRecipientResolver;
import com.yzzhang.weeklyreport.service.notification.WorkNoticeException;
import com.yzzhang.weeklyreport.service.reminder.ReminderCandidateProvider;
import com.yzzhang.weeklyreport.service.reminder.ReminderCandidateSnapshot;
import com.yzzhang.weeklyreport.service.reminder.ReminderRunStore;
import com.yzzhang.weeklyreport.service.reminder.ReminderRunStore.ReminderRunState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SubmissionReminderService {
    static final String PHASE_EMPLOYEE_SENDING = "EMPLOYEE_SENDING";
    static final String PHASE_EMPLOYEE_SENT = "EMPLOYEE_SENT";
    static final String PHASE_UNKNOWN = "UNKNOWN";
    static final String PHASE_COMPLETE = "COMPLETE";

    private static final Logger log = LoggerFactory.getLogger(SubmissionReminderService.class);
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReminderCandidateProvider candidateProvider;
    private final ReminderRunStore runStore;
    private final DingTalkWorkNoticeClient workNoticeClient;
    private final FeedbackRecipientResolver recipientResolver;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SubmissionReminderService(
        ReminderCandidateProvider candidateProvider,
        ReminderRunStore runStore,
        DingTalkWorkNoticeClient workNoticeClient,
        FeedbackRecipientResolver recipientResolver
    ) {
        this.candidateProvider = candidateProvider;
        this.runStore = runStore;
        this.workNoticeClient = workNoticeClient;
        this.recipientResolver = recipientResolver;
    }

    public void runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.info("Submission reminder skipped because another run is active.");
            return;
        }

        FeedbackRecipientResolver.Recipient administrator;
        try {
            administrator = recipientResolver.resolve();
        } catch (RuntimeException ex) {
            log.error("Submission reminder aborted: administrator recipient resolution failed.");
            running.set(false);
            return;
        }
        if (administrator.userIds().isEmpty()) {
            log.error("Submission reminder aborted: administrator notification recipient is unavailable.");
            running.set(false);
            return;
        }

        ReminderCandidateSnapshot snapshot = null;
        boolean administratorResultSent = false;
        try {
            snapshot = candidateProvider.collect();
            Optional<ReminderRunState> previous = runStore.load(snapshot.weekLabel());
            if (previous.filter(state -> PHASE_COMPLETE.equals(state.phase())).isPresent()) {
                log.info("Submission reminder already completed for {}.", snapshot.weekLabel());
                return;
            }
            if (previous.filter(state -> PHASE_EMPLOYEE_SENT.equals(state.phase())).isPresent()) {
                sendAdministratorSuccess(administrator, previous.get());
                administratorResultSent = true;
                runStore.save(withPhase(previous.get(), PHASE_COMPLETE));
                return;
            }
            if (previous.filter(state -> PHASE_EMPLOYEE_SENDING.equals(state.phase()) || PHASE_UNKNOWN.equals(state.phase())).isPresent()) {
                administratorResultSent = sendAdministratorFailure(
                    administrator,
                    snapshot,
                    "上次员工提醒发送结果不确定，系统未自动重发"
                );
                runStore.save(runStore.state(snapshot, PHASE_UNKNOWN));
                return;
            }

            if (!snapshot.missingUserIds().isEmpty()) {
                runStore.save(runStore.state(snapshot, PHASE_EMPLOYEE_SENDING));
                try {
                    workNoticeClient.sendMarkdown(
                        "周报提交提醒",
                        employeeReminder(snapshot),
                        snapshot.missingUserIds()
                    );
                } catch (WorkNoticeException ex) {
                    runStore.save(runStore.state(snapshot, PHASE_UNKNOWN));
                    administratorResultSent = sendAdministratorFailure(administrator, snapshot, ex.getMessage());
                    return;
                }
            }

            runStore.save(runStore.state(snapshot, PHASE_EMPLOYEE_SENT));
            sendAdministratorSuccess(administrator, runStore.state(snapshot, PHASE_EMPLOYEE_SENT));
            administratorResultSent = true;
            runStore.save(runStore.state(snapshot, PHASE_COMPLETE));
        } catch (RuntimeException ex) {
            if (!administratorResultSent) {
                administratorResultSent = sendAdministratorFailure(administrator, snapshot, safeReason(ex));
            }
            log.warn("Submission reminder failed; administratorNotified={}", administratorResultSent);
        } finally {
            running.set(false);
        }
    }

    private String employeeReminder(ReminderCandidateSnapshot snapshot) {
        return """
            ### 周报提交提醒

            截至本周日 18:00，系统尚未检测到您提交本周指定模板的周报，请及时检查并提交。

            - **业务周**：%s
            - **当前状态**：尚未检测到提交
            - **如您已提交**：请确认模板、提交账号及提交时间是否正确

            本消息由周报系统自动发送，请勿直接回复。如有其他疑问请联系张艺政。
            """.formatted(snapshot.weekLabel()).strip();
    }

    private void sendAdministratorSuccess(
        FeedbackRecipientResolver.Recipient administrator,
        ReminderRunState state
    ) {
        String submissionResult = state.missingCount() == 0 ? "全部已提交" : "仍有未提交人员，员工提醒已发送";
        String markdown = """
            ### 周报提醒自动检测结果

            - **任务执行**：成功
            - **提交检查**：%s
            - **业务周**：%s
            - **应交人数**：%d
            - **已交人数**：%d
            - **未交人数**：%d
            - **完成时间**：%s

            结果通知仅包含汇总数字，详细名单请在授权的周报系统中查看。
            """.formatted(
            submissionResult,
            state.weekLabel(),
            state.expectedCount(),
            state.submittedCount(),
            state.missingCount(),
            nowText()
        ).strip();
        workNoticeClient.sendMarkdown("周报提醒自动检测：成功", markdown, administrator.userIds());
    }

    private boolean sendAdministratorFailure(
        FeedbackRecipientResolver.Recipient administrator,
        ReminderCandidateSnapshot snapshot,
        String reason
    ) {
        String week = snapshot == null ? "无法确定" : snapshot.weekLabel();
        String markdown = """
            ### 周报提醒自动检测结果

            - **任务执行**：失败
            - **业务周**：%s
            - **安全原因**：%s
            - **处理结果**：未基于不完整数据继续自动提醒
            - **完成时间**：%s

            请检查服务器任务状态和钉钉应用配置；本通知不包含 token、userid 或原始接口响应。
            """.formatted(week, safeReason(reason), nowText()).strip();
        try {
            workNoticeClient.sendMarkdown("周报提醒自动检测：失败", markdown, administrator.userIds());
            return true;
        } catch (WorkNoticeException ex) {
            log.error("Submission reminder administrator failure notification could not be delivered.");
            return false;
        }
    }

    private String safeReason(Throwable throwable) {
        return safeReason(throwable == null ? "未知错误" : throwable.getMessage());
    }

    private String safeReason(String value) {
        String safe = value == null || value.isBlank() ? "未知错误" : value;
        safe = safe.replaceAll("(?i)access[_-]?token[=:][^,;&\\s]+", "access_token=***");
        safe = safe.replaceAll("(?i)appsecret[=:][^,;&\\s]+", "appSecret=***");
        safe = safe.replaceAll("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer ***");
        safe = safe.replace('\r', ' ').replace('\n', ' ').strip();
        return safe.substring(0, Math.min(safe.length(), 160));
    }

    private String nowText() {
        return DISPLAY_TIME.format(ZonedDateTime.now(CN_ZONE));
    }

    private ReminderRunState withPhase(ReminderRunState state, String phase) {
        return new ReminderRunState(
            state.weekLabel(),
            phase,
            state.expectedCount(),
            state.submittedCount(),
            state.missingCount(),
            ZonedDateTime.now(CN_ZONE).toInstant().toString()
        );
    }
}
