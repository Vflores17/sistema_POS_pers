package com.vflores.pos.auth.infrastructure.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public class AuthenticatedUser implements UserDetails {

    private final UUID id;
    private final String username;
    private final String password;
    private final String email;
    private final String fullName;
    private final boolean active;
    private final boolean blocked;
    private final Set<GrantedAuthority> authorities;

    public AuthenticatedUser(
            UUID id,
            String username,
            String password,
            String email,
            String fullName,
            boolean active,
            boolean blocked,
            Set<GrantedAuthority> authorities
    ) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.fullName = fullName;
        this.active = active;
        this.blocked = blocked;
        this.authorities = authorities;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !blocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
