package com.yzzhang.weeklyreport.controller;

import com.yzzhang.weeklyreport.service.EvaluationFeedbackPreviewService;
import com.yzzhang.weeklyreport.vo.EvaluationFeedbackPreviewVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/evaluation-feedback-previews")
public class EvaluationFeedbackPreviewController {
    private final EvaluationFeedbackPreviewService previewService;

    public EvaluationFeedbackPreviewController(EvaluationFeedbackPreviewService previewService) {
        this.previewService = previewService;
    }

    @GetMapping("/{week}")
    public EvaluationFeedbackPreviewVO getPreview(@PathVariable String week) {
        return previewService.getPreview(week);
    }
}
