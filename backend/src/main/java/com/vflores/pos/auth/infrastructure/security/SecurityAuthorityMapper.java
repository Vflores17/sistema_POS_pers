package com.vflores.pos.auth.infrastructure.security;

import com.vflores.pos.auth.application.EffectivePermissionService;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.users.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SecurityAuthorityMapper {

    private final EffectivePermissionService effectivePermissionService;

    public Set<GrantedAuthority> mapAuthorities(User user) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        for (Role role : user.getRoles()) {
            if (!role.isActive()) {
                continue;
            }

            authorities.add(new SimpleGrantedAuthority("ROLE_" + normalize(role.getName())));
        }

        effectivePermissionService.resolve(user).effective().stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

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
