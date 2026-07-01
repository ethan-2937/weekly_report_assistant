package com.yzzhang.weeklyreport.service;

import com.yzzhang.weeklyreport.vo.FeedbackRequestVO;
import com.yzzhang.weeklyreport.vo.FeedbackResponseVO;
import jakarta.servlet.http.HttpServletRequest;

public interface FeedbackService {
    FeedbackResponseVO submit(FeedbackRequestVO requestVO, HttpServletRequest request);
}
