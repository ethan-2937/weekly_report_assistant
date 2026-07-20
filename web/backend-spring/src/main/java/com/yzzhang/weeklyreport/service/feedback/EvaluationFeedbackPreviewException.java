package com.yzzhang.weeklyreport.service.feedback;

public class EvaluationFeedbackPreviewException extends RuntimeException {
    public enum Reason {
        NOT_COMPLETE,
        CONTENT_UNAVAILABLE
    }

    private final Reason reason;

    public EvaluationFeedbackPreviewException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
