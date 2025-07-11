package com.medicorex.security;

import com.medicorex.entity.User;
import lombok.AllArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@AllArgsConstructor
public class CustomUserDetails implements UserDetails {

    private User user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String role = "ROLE_" + user.getRole().name();
        System.out.println("=== CustomUserDetails - Granting authority: " + role);
        return Collections.singleton(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
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
        boolean isActive = user.getActive() != null && user.getActive();
        System.out.println("=== CustomUserDetails - isAccountNonLocked: " + isActive);
        return isActive;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        boolean isActive = user.getActive() != null && user.getActive();
        System.out.println("=== CustomUserDetails - isEnabled: " + isActive);
        return isActive;
    }

    public User getUser() {
        return user;
    }
}