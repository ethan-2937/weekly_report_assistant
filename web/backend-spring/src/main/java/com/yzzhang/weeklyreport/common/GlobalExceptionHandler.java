package com.yzzhang.weeklyreport.common;

import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackPreviewException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "周次或文件不存在"));
    }

    @ExceptionHandler(ExportUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleExportUnavailable(ExportUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "原周报快照不完整，请重新采集该周次后再下载"));
    }

    @ExceptionHandler(EvaluationFeedbackPreviewException.class)
    public ResponseEntity<Map<String, String>> handleFeedbackPreviewUnavailable(
        EvaluationFeedbackPreviewException ex
    ) {
        String message = ex.reason() == EvaluationFeedbackPreviewException.Reason.NOT_COMPLETE
            ? "该周反馈通知尚未完整发送，暂无可复核内容"
            : "该周反馈通知暂时无法安全复核";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", message));
    }

    @ExceptionHandler({BizException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "请求参数无效"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage() == null ? "参数错误" : error.getDefaultMessage())
            .orElse("参数错误");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleServerError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "服务暂时不可用，请稍后重试"));
    }
}
