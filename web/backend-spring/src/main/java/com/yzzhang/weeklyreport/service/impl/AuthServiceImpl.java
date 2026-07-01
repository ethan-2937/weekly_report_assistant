package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.common.BizException;
import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import com.yzzhang.weeklyreport.mapper.SysLoginLogMapper;
import com.yzzhang.weeklyreport.mapper.SysUserMapper;
import com.yzzhang.weeklyreport.po.SysUserPO;
import com.yzzhang.weeklyreport.security.AuthUserDetailsService;
import com.yzzhang.weeklyreport.security.AuthenticatedUser;
import com.yzzhang.weeklyreport.security.JwtTokenProvider;
import com.yzzhang.weeklyreport.service.AuthService;
import com.yzzhang.weeklyreport.vo.CurrentUserVO;
import com.yzzhang.weeklyreport.vo.DingTalkLoginUrlVO;
import com.yzzhang.weeklyreport.vo.LoginRequestVO;
import com.yzzhang.weeklyreport.vo.LoginResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {
    private final AuthUserDetailsService userDetailsService;
    private final SysUserMapper sysUserMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final WeeklyReportProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public AuthServiceImpl(
        AuthUserDetailsService userDetailsService,
        SysUserMapper sysUserMapper,
        SysLoginLogMapper loginLogMapper,
        JwtTokenProvider jwtTokenProvider,
        PasswordEncoder passwordEncoder,
        WeeklyReportProperties properties
    ) {
        this.userDetailsService = userDetailsService;
        this.sysUserMapper = sysUserMapper;
        this.loginLogMapper = loginLogMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public LoginResponseVO login(LoginRequestVO requestVO, HttpServletRequest request) {
        String username = requestVO.getUsername() == null ? "" : requestVO.getUsername().trim();
        try {
            AuthenticatedUser user = (AuthenticatedUser) userDetailsService.loadUserByUsername(username);
            if (user.getPassword() == null || !passwordEncoder.matches(requestVO.getPassword(), user.getPassword())) {
                recordLogin(null, username, "PASSWORD", false, request, "用户名或密码错误");
                throw new BizException("用户名或密码错误");
            }
            sysUserMapper.updateLastLoginTime(user.getId());
            recordLogin(user.getId(), username, "PASSWORD", true, request, "登录成功");
            return buildLoginResponse(user);
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            recordLogin(null, username, "PASSWORD", false, request, "用户名或密码错误");
            throw new BizException("用户名或密码错误");
        }
    }

    @Override
    public CurrentUserVO currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BizException("请先登录");
        }
        return toCurrentUser(user);
    }

    @Override
    public DingTalkLoginUrlVO dingtalkLoginUrl() {
        WeeklyReportProperties.DingTalk dingtalk = properties.getAuth().getDingtalk();
        DingTalkLoginUrlVO vo = new DingTalkLoginUrlVO();
        if (!dingtalk.isEnabled()) {
            vo.setEnabled(false);
            vo.setMessage("钉钉登录暂未启用");
            return vo;
        }
        if (!hasText(dingtalk.getClientId()) || !hasText(dingtalk.getClientSecret()) || !hasText(dingtalk.getRedirectUri())) {
            vo.setEnabled(false);
            vo.setMessage("钉钉登录配置不完整，请配置 clientId、clientSecret 和 redirectUri");
            return vo;
        }

        String loginUrl = UriComponentsBuilder.fromUriString(dingtalk.getAuthorizeUrl())
            .queryParam("redirect_uri", dingtalk.getRedirectUri())
            .queryParam("response_type", "code")
            .queryParam("client_id", dingtalk.getClientId())
            .queryParam("scope", "openid")
            .queryParam("state", UUID.randomUUID().toString())
            .queryParam("prompt", "consent")
            .build()
            .encode()
            .toUriString();
        vo.setEnabled(true);
        vo.setLoginUrl(loginUrl);
        return vo;
    }

    @Override
    public LoginResponseVO loginByDingTalk(String code, HttpServletRequest request) {
        if (!hasText(code)) {
            throw new BizException("钉钉授权码为空");
        }
        DingTalkProfile profile = fetchDingTalkProfile(code);
        Optional<SysUserPO> userOptional = sysUserMapper.findByDingIdentity(profile.userId(), profile.unionId());
        if (userOptional.isEmpty()) {
            recordLogin(null, profile.nick(), "DINGTALK", false, request, "钉钉账号未绑定系统用户");
            throw new BizException("该钉钉账号尚未绑定系统用户，请先由管理员绑定");
        }

        SysUserPO userPO = userOptional.get();
        AuthenticatedUser user = (AuthenticatedUser) userDetailsService.loadUserByUsername(userPO.getUsername());
        sysUserMapper.updateLastLoginTime(user.getId());
        recordLogin(user.getId(), user.getUsername(), "DINGTALK", true, request, "登录成功");
        return buildLoginResponse(user);
    }

    @Override
    public String dingtalkFrontendUrl() {
        String frontendUrl = properties.getAuth().getDingtalk().getFrontendUrl();
        return hasText(frontendUrl) ? frontendUrl : "/";
    }

    private LoginResponseVO buildLoginResponse(AuthenticatedUser user) {
        LoginResponseVO response = new LoginResponseVO();
        response.setToken(jwtTokenProvider.generateToken(user));
        response.setExpiresIn(jwtTokenProvider.getExpiresInSeconds());
        response.setUser(toCurrentUser(user));
        return response;
    }

    private CurrentUserVO toCurrentUser(AuthenticatedUser user) {
        CurrentUserVO vo = new CurrentUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
        vo.setDingUserId(user.getDingUserId());
        vo.setRoles(user.getRoles());
        vo.setDeptScopes(user.getDeptScopes());
        return vo;
    }

    @SuppressWarnings("unchecked")
    private DingTalkProfile fetchDingTalkProfile(String code) {
        WeeklyReportProperties.DingTalk dingtalk = properties.getAuth().getDingtalk();
        if (!dingtalk.isEnabled()) {
            throw new BizException("钉钉登录暂未启用");
        }

        Map<String, Object> tokenRequest = new HashMap<>();
        tokenRequest.put("clientId", dingtalk.getClientId());
        tokenRequest.put("clientSecret", dingtalk.getClientSecret());
        tokenRequest.put("code", code);
        tokenRequest.put("grantType", "authorization_code");

        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(dingtalk.getTokenUrl(), tokenRequest, Map.class);
        Map<String, Object> tokenBody = tokenResponse.getBody();
        String accessToken = stringValue(tokenBody, List.of("accessToken", "access_token"));
        if (!hasText(accessToken)) {
            throw new BizException("钉钉登录失败：未获取到用户访问凭证");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-acs-dingtalk-access-token", accessToken);
        ResponseEntity<Map> userResponse = restTemplate.exchange(
            dingtalk.getUserInfoUrl(),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );
        Map<String, Object> userBody = userResponse.getBody();
        String userId = stringValue(userBody, List.of("userId", "userid", "user_id"));
        String unionId = stringValue(userBody, List.of("unionId", "unionid", "union_id"));
        String nick = stringValue(userBody, List.of("nick", "name", "realName"));
        return new DingTalkProfile(userId, unionId, nick);
    }

    private String stringValue(Map<String, Object> map, List<String> keys) {
        if (map == null) {
            return "";
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private void recordLogin(Long userId, String username, String loginType, boolean success, HttpServletRequest request, String message) {
        try {
            loginLogMapper.insert(userId, username, loginType, success, clientIp(request), request.getHeader("User-Agent"), message);
        } catch (RuntimeException ignored) {
            // Login logs should not block users from entering the weekly report system.
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record DingTalkProfile(String userId, String unionId, String nick) {
    }
}
