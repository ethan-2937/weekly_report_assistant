package com.yzzhang.weeklyreport.service;

import com.yzzhang.weeklyreport.vo.CurrentUserVO;
import com.yzzhang.weeklyreport.vo.DingTalkLoginUrlVO;
import com.yzzhang.weeklyreport.vo.LoginRequestVO;
import com.yzzhang.weeklyreport.vo.LoginResponseVO;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {
    LoginResponseVO login(LoginRequestVO requestVO, HttpServletRequest request);

    CurrentUserVO currentUser();

    DingTalkLoginUrlVO dingtalkLoginUrl();

    LoginResponseVO loginByDingTalk(String code, HttpServletRequest request);

    String dingtalkFrontendUrl();
}
