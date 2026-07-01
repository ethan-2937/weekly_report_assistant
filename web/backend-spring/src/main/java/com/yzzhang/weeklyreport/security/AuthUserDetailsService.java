package com.yzzhang.weeklyreport.security;

import com.yzzhang.weeklyreport.mapper.SysUserMapper;
import com.yzzhang.weeklyreport.po.SysUserPO;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthUserDetailsService implements UserDetailsService {
    private final SysUserMapper sysUserMapper;

    public AuthUserDetailsService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUserPO user = sysUserMapper.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new DisabledException("User is disabled");
        }
        List<String> roles = sysUserMapper.findRoleCodesByUserId(user.getId());
        List<String> deptScopes = sysUserMapper.findDeptScopesByUserId(user.getId());
        return new AuthenticatedUser(user, roles, deptScopes);
    }
}
