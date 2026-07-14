package com.yzzhang.weeklyreport.service.reminder;

public class SubmissionReminderException extends RuntimeException {
    public SubmissionReminderException(String message) {
        super(message);
    }

    public SubmissionReminderException(String message, Throwable cause) {
        super(message, cause);
    }
}
