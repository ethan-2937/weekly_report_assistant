package com.yzzhang.weeklyreport.service;

import com.yzzhang.weeklyreport.vo.AdminUserSaveRequestVO;
import com.yzzhang.weeklyreport.vo.AdminUserVO;
import com.yzzhang.weeklyreport.vo.PasswordResetRequestVO;
import com.yzzhang.weeklyreport.vo.RoleVO;

import java.util.List;

public interface AdminUserService {
    List<AdminUserVO> listUsers(String keyword);

    AdminUserVO createUser(AdminUserSaveRequestVO request);

    AdminUserVO updateUser(Long id, AdminUserSaveRequestVO request);

    void resetPassword(Long id, PasswordResetRequestVO request);

    List<RoleVO> listRoles();
}
