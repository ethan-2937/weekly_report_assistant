package com.yzzhang.weeklyreport.config;

import com.yzzhang.weeklyreport.common.ResourceNotFoundException;
import com.yzzhang.weeklyreport.controller.JobController;
import com.yzzhang.weeklyreport.controller.ProjectDetailController;
import com.yzzhang.weeklyreport.controller.WeekController;
import com.yzzhang.weeklyreport.security.AuthUserDetailsService;
import com.yzzhang.weeklyreport.security.JwtAuthenticationFilter;
import com.yzzhang.weeklyreport.security.JwtTokenProvider;
import com.yzzhang.weeklyreport.service.JobService;
import com.yzzhang.weeklyreport.service.ProjectDetailService;
import com.yzzhang.weeklyreport.service.WeeklyReportService;
import com.yzzhang.weeklyreport.service.WeeklyReportSourceService;
import com.yzzhang.weeklyreport.vo.JobRecordVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = {WeekController.class, ProjectDetailController.class, JobController.class},
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class)
)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityConfigWebMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WeeklyReportService weeklyReportService;

    @MockBean
    private WeeklyReportSourceService weeklyReportSourceService;

    @MockBean
    private ProjectDetailService projectDetailService;

    @MockBean
    private JobService jobService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private AuthUserDetailsService authUserDetailsService;

    @Test
    void unauthenticatedReportRequestReturnsSanitizedUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/weeks"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("请先登录"))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.anyOf(
                    org.hamcrest.Matchers.containsString("虚构周报正文"),
                    org.hamcrest.Matchers.containsString("fictional-token"),
                    org.hamcrest.Matchers.containsString("WEEKLY_JWT_SECRET")
                )
            )));
    }

    @Test
    void unauthenticatedReportSourceRequestReturnsSanitizedUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/weeks/2026-W28/reports/test-user-001"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("请先登录"))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.anyOf(
                    org.hamcrest.Matchers.containsString("虚构周报正文"),
                    org.hamcrest.Matchers.containsString("fictional-token"),
                    org.hamcrest.Matchers.containsString("WEEKLY_JWT_SECRET")
                )
            )));
    }

    @Test
    void unauthenticatedProjectDetailRequestReturnsSanitizedUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/weeks/2026-W29/project-details"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("请先登录"))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.anyOf(
                    org.hamcrest.Matchers.containsString("test-user-001"),
                    org.hamcrest.Matchers.containsString("fictional-token"),
                    org.hamcrest.Matchers.containsString("虚构周报正文")
                )
            )));
    }

    @Test
    @WithMockUser(username = "test-scope-user", roles = "USER")
    void scopedUserCannotReadJobEndpoints() throws Exception {
        mockMvc.perform(get("/api/jobs/latest"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("没有访问权限"));
    }

    @Test
    @WithMockUser(username = "test-admin", roles = "ADMIN")
    void adminCanReadJobEndpoints() throws Exception {
        when(jobService.latestJob()).thenReturn(new JobRecordVO());

        mockMvc.perform(get("/api/jobs/latest"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "test-report-all", roles = "REPORT_ALL")
    void reportAllCanReadJobEndpoints() throws Exception {
        when(jobService.latestJob()).thenReturn(new JobRecordVO());

        mockMvc.perform(get("/api/jobs/latest"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "test-scope-user", roles = "USER")
    void authenticatedScopedUserCanReachReportService() throws Exception {
        when(weeklyReportService.listWeeks()).thenReturn(List.of());

        mockMvc.perform(get("/api/weeks"))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    @WithMockUser(username = "test-scope-user", roles = "USER")
    void outOfScopeReportSourceUsesSanitizedNotFoundResponse() throws Exception {
        when(weeklyReportSourceService.getReport("2026-W28", "test-user-002"))
            .thenThrow(new ResourceNotFoundException(
                "test-user-002 fictional-token 虚构周报正文"
            ));

        mockMvc.perform(get("/api/weeks/2026-W28/reports/test-user-002"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("周次或文件不存在"))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.anyOf(
                    org.hamcrest.Matchers.containsString("test-user-002"),
                    org.hamcrest.Matchers.containsString("fictional-token"),
                    org.hamcrest.Matchers.containsString("虚构周报正文")
                )
            )));
    }

    @Test
    @WithMockUser(username = "test-no-scope-user", roles = "USER")
    void serviceLayerScopeRejectionReturnsSanitizedForbiddenResponse() throws Exception {
        when(weeklyReportService.listWeeks()).thenThrow(
            new AccessDeniedException("当前账号没有周报查看范围，请联系管理员配置权限范围")
        );

        mockMvc.perform(get("/api/weeks"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("当前账号没有周报查看范围，请联系管理员配置权限范围"))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.anyOf(
                    org.hamcrest.Matchers.containsString("虚构周报正文"),
                    org.hamcrest.Matchers.containsString("fictional-token"),
                    org.hamcrest.Matchers.containsString("WEEKLY_JWT_SECRET")
                )
            )));
    }

    @Test
    @WithMockUser(username = "test-scope-user", roles = "USER")
    void unexpectedServerErrorsDoNotExposeSensitiveExceptionMessages() throws Exception {
        when(weeklyReportService.listWeeks()).thenThrow(
            new RuntimeException("fictional-token 虚构周报正文 WEEKLY_JWT_SECRET")
        );

        mockMvc.perform(get("/api/weeks"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("服务暂时不可用，请稍后重试"))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.anyOf(
                    org.hamcrest.Matchers.containsString("fictional-token"),
                    org.hamcrest.Matchers.containsString("虚构周报正文"),
                    org.hamcrest.Matchers.containsString("WEEKLY_JWT_SECRET")
                )
            )));
    }
}
