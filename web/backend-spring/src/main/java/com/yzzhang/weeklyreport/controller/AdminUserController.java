package com.yzzhang.weeklyreport.controller;

import com.yzzhang.weeklyreport.service.AdminUserService;
import com.yzzhang.weeklyreport.vo.AdminUserSaveRequestVO;
import com.yzzhang.weeklyreport.vo.AdminUserVO;
import com.yzzhang.weeklyreport.vo.PasswordResetRequestVO;
import com.yzzhang.weeklyreport.vo.RoleVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminUserController {
    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    public List<AdminUserVO> listUsers(@RequestParam(required = false) String keyword) {
        return adminUserService.listUsers(keyword);
    }

    @PostMapping("/users")
    public AdminUserVO createUser(@Valid @RequestBody AdminUserSaveRequestVO request) {
        return adminUserService.createUser(request);
    }

    @PutMapping("/users/{id}")
    public AdminUserVO updateUser(@PathVariable Long id, @Valid @RequestBody AdminUserSaveRequestVO request) {
        return adminUserService.updateUser(id, request);
    }

    @PostMapping("/users/{id}/password")
    public Map<String, String> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequestVO request) {
        adminUserService.resetPassword(id, request);
        return Map.of("message", "密码已重置");
    }

    @GetMapping("/roles")
    public List<RoleVO> listRoles() {
        return adminUserService.listRoles();
    }
}
