package com.yzzhang.weeklyreport.service.feedback;

public class EvaluationFeedbackException extends RuntimeException {
    public EvaluationFeedbackException(String message) {
        super(message);
    }

    public EvaluationFeedbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
