package com.yzzhang.weeklyreport.service;

import com.yzzhang.weeklyreport.vo.EvaluationFeedbackPreviewVO;

public interface EvaluationFeedbackPreviewService {
    EvaluationFeedbackPreviewVO getPreview(String weekLabel);
}
