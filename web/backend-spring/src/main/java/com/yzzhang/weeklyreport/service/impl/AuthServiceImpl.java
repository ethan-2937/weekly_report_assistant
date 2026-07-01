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
import com.yzzhang.weeklyreport.vo.ChangePasswordRequestVO;
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

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthServiceImpl implements AuthService {
    private static final Duration DINGTALK_STATE_TTL = Duration.ofMinutes(10);

    private final AuthUserDetailsService userDetailsService;
    private final SysUserMapper sysUserMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final WeeklyReportProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, Instant> dingtalkStates = new ConcurrentHashMap<>();

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
        return toCurrentUser(currentAuthenticatedUser());
    }

    @Override
    public void changePassword(ChangePasswordRequestVO requestVO) {
        AuthenticatedUser user = currentAuthenticatedUser();
        if (!hasText(user.getPassword())) {
            throw new BizException("当前账号未设置密码，请联系管理员重置后再修改");
        }
        if (!passwordEncoder.matches(requestVO.getOldPassword(), user.getPassword())) {
            throw new BizException("当前密码不正确");
        }
        if (!hasText(requestVO.getNewPassword()) || requestVO.getNewPassword().length() < 6) {
            throw new BizException("新密码至少需要6位");
        }
        sysUserMapper.updatePassword(user.getId(), passwordEncoder.encode(requestVO.getNewPassword()));
    }

    private AuthenticatedUser currentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BizException("请先登录");
        }
        return user;
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

        String state = createDingTalkState();
        String loginUrl = UriComponentsBuilder.fromUriString(dingtalk.getAuthorizeUrl())
            .queryParam("redirect_uri", dingtalk.getRedirectUri())
            .queryParam("response_type", "code")
            .queryParam("client_id", dingtalk.getClientId())
            .queryParam("scope", "openid")
            .queryParam("state", state)
            .queryParam("prompt", "consent")
            .build()
            .encode()
            .toUriString();
        vo.setEnabled(true);
        vo.setLoginUrl(loginUrl);
        return vo;
    }

    @Override
    public LoginResponseVO loginByDingTalk(String code, String state, HttpServletRequest request) {
        validateDingTalkState(state);
        if (!hasText(code)) {
            throw new BizException("钉钉授权码为空");
        }
        DingTalkProfile profile = fetchDingTalkProfile(code);
        Optional<SysUserPO> userOptional = sysUserMapper.findByDingIdentity(profile.userId(), profile.unionId());
        if (userOptional.isEmpty()) {
            userOptional = autoBindLocalUser(profile);
        }
        if (userOptional.isEmpty()) {
            recordLogin(null, profile.displayName(), "DINGTALK", false, request, "钉钉账号未绑定系统用户");
            throw new BizException("该钉钉账号尚未绑定系统用户，请先由管理员在用户管理中绑定");
        }

        SysUserPO userPO = userOptional.get();
        AuthenticatedUser user = (AuthenticatedUser) userDetailsService.loadUserByUsername(userPO.getUsername());
        sysUserMapper.updateLastLoginTime(user.getId());
        recordLogin(user.getId(), user.getUsername(), "DINGTALK", true, request, "登录成功");
        return buildLoginResponse(user);
    }

    private String createDingTalkState() {
        cleanupExpiredDingTalkStates();
        String state = UUID.randomUUID().toString();
        dingtalkStates.put(state, Instant.now().plus(DINGTALK_STATE_TTL));
        return state;
    }

    private void validateDingTalkState(String state) {
        cleanupExpiredDingTalkStates();
        if (!hasText(state)) {
            throw new BizException("钉钉登录状态校验失败，请重新发起登录");
        }
        Instant expiresAt = dingtalkStates.remove(state);
        if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
            throw new BizException("钉钉登录状态已过期，请重新发起登录");
        }
    }

    private void cleanupExpiredDingTalkStates() {
        Instant now = Instant.now();
        dingtalkStates.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }

    private Optional<SysUserPO> autoBindLocalUser(DingTalkProfile profile) {
        if (!hasText(profile.name())) {
            return Optional.empty();
        }
        List<SysUserPO> candidates = sysUserMapper.findActiveByRealName(profile.name());
        if (candidates.size() > 1) {
            throw new BizException("钉钉姓名匹配到多个系统账号，请联系管理员手动绑定");
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        SysUserPO user = candidates.get(0);
        if (hasText(user.getDingUserId()) || hasText(user.getDingUnionId())) {
            return Optional.empty();
        }
        if (sysUserMapper.findRoleCodesByUserId(user.getId()).contains("ADMIN")) {
            throw new BizException("管理员账号需要手动绑定钉钉身份");
        }
        sysUserMapper.bindDingIdentityIfEmpty(user.getId(), profile.userId(), profile.unionId());
        return sysUserMapper.findById(user.getId());
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
        String accessToken = stringValueDeep(tokenBody, List.of("accessToken", "access_token"));
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
        String userId = stringValueDeep(userBody, List.of("userId", "userid", "user_id"));
        String unionId = stringValueDeep(userBody, List.of("unionId", "unionid", "union_id"));
        String name = stringValueDeep(userBody, List.of("name", "nick", "realName", "displayName"));
        if (!hasText(userId) && !hasText(unionId)) {
            throw new BizException("钉钉登录失败：未获取到用户身份标识");
        }
        return new DingTalkProfile(userId, unionId, name);
    }

    private String stringValueDeep(Map<String, Object> map, List<String> keys) {
        if (map == null) {
            return "";
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        for (Object value : map.values()) {
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                String nestedValue = stringValueDeep((Map<String, Object>) nested, keys);
                if (hasText(nestedValue)) {
                    return nestedValue;
                }
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

    private record DingTalkProfile(String userId, String unionId, String name) {
        private String displayName() {
            return hasText(name) ? name : (hasText(userId) ? userId : unionId);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
