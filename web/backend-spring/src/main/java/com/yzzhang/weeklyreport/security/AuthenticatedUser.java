package com.yzzhang.weeklyreport.security;

import com.yzzhang.weeklyreport.po.SysUserPO;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AuthenticatedUser implements UserDetails {
    private final SysUserPO user;
    private final List<String> roles;
    private final List<String> deptScopes;
    private final List<GrantedAuthority> authorities;

    public AuthenticatedUser(SysUserPO user, List<String> roles, List<String> deptScopes) {
        this.user = user;
        this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
        this.deptScopes = deptScopes == null ? new ArrayList<>() : new ArrayList<>(deptScopes);
        this.authorities = this.roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .map(GrantedAuthority.class::cast)
            .toList();
    }

    public Long getId() {
        return user.getId();
    }

    public String getRealName() {
        return user.getRealName();
    }

    public String getDingUserId() {
        return user.getDingUserId();
    }

    public List<String> getRoles() {
        return new ArrayList<>(roles);
    }

    public List<String> getDeptScopes() {
        return new ArrayList<>(deptScopes);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() != null && user.getStatus() == 1;
    }
}
