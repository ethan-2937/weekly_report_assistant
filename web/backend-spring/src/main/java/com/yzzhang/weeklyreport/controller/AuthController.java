package com.yzzhang.weeklyreport.controller;

import com.yzzhang.weeklyreport.service.AuthService;
import com.yzzhang.weeklyreport.vo.CurrentUserVO;
import com.yzzhang.weeklyreport.vo.DingTalkLoginUrlVO;
import com.yzzhang.weeklyreport.vo.LoginRequestVO;
import com.yzzhang.weeklyreport.vo.LoginResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponseVO login(@Valid @RequestBody LoginRequestVO loginRequest, HttpServletRequest request) {
        return authService.login(loginRequest, request);
    }

    @GetMapping("/me")
    public CurrentUserVO me() {
        return authService.currentUser();
    }

    @PostMapping("/logout")
    public Map<String, String> logout() {
        return Map.of("message", "已退出登录");
    }

    @GetMapping("/dingtalk/login-url")
    public DingTalkLoginUrlVO dingtalkLoginUrl() {
        return authService.dingtalkLoginUrl();
    }

    @GetMapping("/dingtalk/callback")
    public ResponseEntity<Void> dingtalkCallback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String authCode,
        HttpServletRequest request
    ) {
        String frontendUrl = authService.dingtalkFrontendUrl();
        try {
            LoginResponseVO login = authService.loginByDingTalk(code != null ? code : authCode, request);
            URI redirect = UriComponentsBuilder.fromUriString(frontendUrl)
                .queryParam("token", login.getToken())
                .queryParam("login", "dingtalk")
                .build()
                .encode()
                .toUri();
            return redirect(redirect);
        } catch (RuntimeException ex) {
            URI redirect = UriComponentsBuilder.fromUriString(frontendUrl)
                .queryParam("auth_error", ex.getMessage())
                .build()
                .encode()
                .toUri();
            return redirect(redirect);
        }
    }

    private ResponseEntity<Void> redirect(URI uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(uri);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
