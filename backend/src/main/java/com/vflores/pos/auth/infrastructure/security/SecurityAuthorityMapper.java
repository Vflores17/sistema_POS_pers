package com.vflores.pos.auth.infrastructure.security;

import com.vflores.pos.roles.domain.model.Permission;
import com.vflores.pos.roles.domain.model.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SecurityAuthorityMapper {

    public Set<GrantedAuthority> mapAuthorities(Collection<Role> roles) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        for (Role role : roles) {
            if (!role.isActive()) {
                continue;
            }

            authorities.add(new SimpleGrantedAuthority("ROLE_" + normalize(role.getName())));

            for (Permission permission : role.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(normalize(permission.getCode())));
            }
        }

        return authorities;
    }

    public Set<String> roleNames(Collection<Role> roles) {
        return roles.stream()
                .filter(Role::isActive)
                .map(Role::getName)
                .map(this::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
