package com.yzzhang.weeklyreport.service.feedback;

import com.yzzhang.weeklyreport.config.EvaluationFeedbackProperties;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackSnapshot.EmployeeFeedback;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
public class EvaluationFeedbackMessageFormatter {
    private final EvaluationFeedbackProperties properties;

    public EvaluationFeedbackMessageFormatter(EvaluationFeedbackProperties properties) {
        this.properties = properties;
    }

    public String format(String weekLabel, EmployeeFeedback employee) {
        return """
            ### %s，您的 %s 周报评价

            #### 周报模板符合度

            %d%%

            #### 做得好的地方

            %s

            #### 建议重点改进

            %s

            %s

            ---

            如有疑问请联系HR%s
            """.formatted(
                employee.name(),
                weekLabel,
                employee.templateComplianceRate(),
                employee.praise(),
                employee.improvement(),
                employee.thanks(),
                properties.getHrContactName().trim()
            ).strip();
    }

    public String digest(String weekLabel, List<EmployeeFeedback> employees) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (EmployeeFeedback employee : employees) {
                byte[] message = format(weekLabel, employee).getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(message.length).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(message);
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new EvaluationFeedbackException("评价通知摘要生成失败", ex);
        }
    }
}
