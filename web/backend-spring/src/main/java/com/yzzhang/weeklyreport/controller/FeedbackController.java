package com.yzzhang.weeklyreport.controller;

import com.yzzhang.weeklyreport.service.FeedbackService;
import com.yzzhang.weeklyreport.vo.FeedbackRequestVO;
import com.yzzhang.weeklyreport.vo.FeedbackResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public FeedbackResponseVO submit(@Valid @RequestBody FeedbackRequestVO requestVO, HttpServletRequest request) {
        return feedbackService.submit(requestVO, request);
    }
}
