package com.yzzhang.weeklyreport.controller;

import com.yzzhang.weeklyreport.service.NotificationTestService;
import com.yzzhang.weeklyreport.vo.NotificationTestRequestVO;
import com.yzzhang.weeklyreport.vo.NotificationTestResultVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notification-tests")
public class NotificationTestController {
    private final NotificationTestService notificationTestService;

    public NotificationTestController(NotificationTestService notificationTestService) {
        this.notificationTestService = notificationTestService;
    }

    @PostMapping
    public NotificationTestResultVO send(@Valid @RequestBody NotificationTestRequestVO request) {
        return notificationTestService.send(request);
    }
}
