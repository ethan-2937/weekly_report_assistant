package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import com.yzzhang.weeklyreport.po.SysUserPO;
import com.yzzhang.weeklyreport.security.AuthenticatedUser;
import com.yzzhang.weeklyreport.service.ReportPermissionService.ReportPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportPermissionServiceImplTest {
    private final ReportPermissionServiceImpl service = new ReportPermissionServiceImpl();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reportAllCanReadEveryReport() {
        authenticate(List.of("REPORT_ALL"), List.of());

        ReportPermission permission = service.currentPermission();

        assertThat(permission.fullAccess()).isTrue();
        assertThat(service.filterRows(sampleRows(), permission)).hasSize(3);
    }

    @Test
    void adminDoesNotGainReportAccessWithoutAnExplicitScope() {
        authenticate(List.of("ADMIN"), List.of());

        assertThatThrownBy(service::currentPermission)
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("权限范围")
            .hasMessageNotContaining("虚构周报正文")
            .hasMessageNotContaining("fictional-token")
            .hasMessageNotContaining("WEEKLY_JWT_SECRET");
    }

    @Test
    void adminWithDepartmentScopeOnlyReadsThatDepartment() {
        authenticate(List.of("ADMIN"), List.of("DEPT:测试研发部"));

        ReportPermission permission = service.currentPermission();

        assertThat(permission.fullAccess()).isFalse();
        assertThat(names(service.filterRows(sampleRows(), permission)))
            .containsExactly("示例员工甲", "示例员工丙");
    }

    @Test
    void departmentScopeDoesNotExpandToOtherDepartments() {
        authenticate(List.of("USER"), List.of("DEPT:测试研发部"));

        assertThat(names(service.filterRows(sampleRows(), service.currentPermission())))
            .containsExactly("示例员工甲", "示例员工丙");
    }

    @Test
    void userScopeMatchesOnlyTheNamedPerson() {
        authenticate(List.of("USER"), List.of("USER:示例员工乙"));

        assertThat(names(service.filterRows(sampleRows(), service.currentPermission())))
            .containsExactly("示例员工乙");
    }

    @Test
    void userIdScopeUsesTheStableUserId() {
        authenticate(List.of("USER"), List.of("USERID:test-user-003"));

        assertThat(names(service.filterRows(sampleRows(), service.currentPermission())))
            .containsExactly("示例员工丙");
    }

    @Test
    void multipleScopesAreUnionedWithoutIncludingUnrelatedRows() {
        authenticate(List.of("USER"), List.of("USERID:test-user-001", "USER:示例员工乙"));

        assertThat(names(service.filterRows(sampleRows(), service.currentPermission())))
            .containsExactly("示例员工甲", "示例员工乙");
    }

    @Test
    void compatiblePlainTextCanMatchDepartmentNameOrUserId() {
        authenticate(List.of("USER"), List.of("测试市场部", "test-user-003"));

        assertThat(names(service.filterRows(sampleRows(), service.currentPermission())))
            .containsExactly("示例员工乙", "示例员工丙");
    }

    @Test
    void noMatchingScopeReturnsAnEmptyRange() {
        authenticate(List.of("USER"), List.of("DEPT:测试法务部"));

        assertThat(service.filterRows(sampleRows(), service.currentPermission())).isEmpty();
    }

    @Test
    void blankScopesAreRejectedInsteadOfLeakingRows() {
        authenticate(List.of("USER"), List.of(" ", ""));

        assertThatThrownBy(service::currentPermission)
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void explicitAllScopeStillGrantsFullReportAccess() {
        authenticate(List.of("USER"), List.of("ALL"));

        assertThat(service.currentPermission().fullAccess()).isTrue();
    }

    private void authenticate(List<String> roles, List<String> scopes) {
        SysUserPO user = new SysUserPO();
        user.setId(101L);
        user.setUsername("test-account-001");
        user.setStatus(1);
        AuthenticatedUser principal = new AuthenticatedUser(user, roles, scopes);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private List<SubmissionStatusPO> sampleRows() {
        return List.of(
            row("示例员工甲", "test-user-001", "测试研发部", ""),
            row("示例员工乙", "test-user-002", "测试市场部", "测试市场部/测试项目组"),
            row("示例员工丙", "test-user-003", "测试平台组", "测试研发部")
        );
    }

    private SubmissionStatusPO row(String name, String userId, String dept, String reportDept) {
        SubmissionStatusPO row = new SubmissionStatusPO();
        row.setName(name);
        row.setUserid(userId);
        row.setDept(dept);
        row.setReportDept(reportDept);
        return row;
    }

    private List<String> names(List<SubmissionStatusPO> rows) {
        return rows.stream().map(SubmissionStatusPO::getName).toList();
    }
}
