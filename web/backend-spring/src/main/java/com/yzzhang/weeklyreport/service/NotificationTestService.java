package com.yzzhang.weeklyreport.service;

import com.yzzhang.weeklyreport.vo.NotificationTestRequestVO;
import com.yzzhang.weeklyreport.vo.NotificationTestResultVO;

public interface NotificationTestService {
    NotificationTestResultVO send(NotificationTestRequestVO request);
}
