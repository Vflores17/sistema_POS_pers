package com.vflores.pos.auth.application;

import com.vflores.pos.roles.domain.model.Permission;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.roles.domain.repository.PermissionRepository;
import com.vflores.pos.users.domain.model.PermissionOverrideEffect;
import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.model.UserPermissionOverride;
import com.vflores.pos.users.domain.repository.UserPermissionOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EffectivePermissionService {

    private static final String ADMIN_ROLE = "ADMIN";

    private static final Map<String, Set<String>> LEGACY_WRITE_EXPANSIONS = Map.of(
            "SALE_WRITE", Set.of("SALE_CREATE", "SALE_UPDATE", "SALE_DELETE", "SALE_CANCEL"),
            "CLIENT_WRITE", Set.of("CLIENT_CREATE", "CLIENT_UPDATE", "CLIENT_DELETE"),
            "PRODUCT_WRITE", Set.of("PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DELETE"),
            "PRICE_WRITE", Set.of("PRICE_CREATE", "PRICE_UPDATE", "PRICE_DELETE"),
            "USER_WRITE", Set.of(
                    "USER_CREATE", "USER_UPDATE", "USER_DELETE", "USER_ASSIGN_ROLE", "USER_ASSIGN_PERMISSION"
            ),
            "ROLE_WRITE", Set.of(
                    "ROLE_CREATE", "ROLE_UPDATE", "ROLE_DELETE", "ROLE_ASSIGN_PERMISSION",
                    "PERMISSION_CREATE", "PERMISSION_UPDATE", "PERMISSION_DELETE"
            ),
            "DRIVER_WRITE", Set.of("DRIVER_CREATE", "DRIVER_UPDATE", "DRIVER_DELETE")
    );

    private final UserPermissionOverrideRepository overrideRepository;
    private final PermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public PermissionResolution resolve(User user) {
        Set<String> inherited = inheritedPermissions(user.getRoles());
        expandLegacyPermissions(inherited);

        List<UserPermissionOverride> overrides = overrideRepository.findAllByUserId(user.getId());
        Set<String> allowed = codesWithEffect(overrides, PermissionOverrideEffect.ALLOW);
        Set<String> denied = codesWithEffect(overrides, PermissionOverrideEffect.DENY);
        expandLegacyPermissions(allowed);
        expandLegacyPermissions(denied);

        Set<String> effective = new LinkedHashSet<>(inherited);
        effective.addAll(allowed);
        if (hasActiveRole(user.getRoles(), ADMIN_ROLE)) {
            permissionRepository.findAll().stream()
                    .map(Permission::getCode)
                    .map(this::normalize)
                    .forEach(effective::add);
            denied = Set.of();
        } else {
            effective.removeAll(denied);
        }

        return new PermissionResolution(inherited, allowed, denied, effective);
    }

    public Set<String> inheritedPermissions(Collection<Role> roles) {
        Set<String> inherited = new LinkedHashSet<>();
        roles.stream()
                .filter(Role::isActive)
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .map(this::normalize)
                .forEach(inherited::add);
        return inherited;
    }

    private void expandLegacyPermissions(Set<String> permissions) {
        LEGACY_WRITE_EXPANSIONS.forEach((legacy, granular) -> {
            if (permissions.contains(legacy)) {
                permissions.addAll(granular);
            }
        });
    }

    private Set<String> codesWithEffect(
            Collection<UserPermissionOverride> overrides,
            PermissionOverrideEffect effect
    ) {
        Set<String> codes = new LinkedHashSet<>();
        overrides.stream()
                .filter(override -> override.getEffect() == effect)
                .map(UserPermissionOverride::getPermission)
                .map(Permission::getCode)
                .map(this::normalize)
                .forEach(codes::add);
        return codes;
    }

    private boolean hasActiveRole(Collection<Role> roles, String name) {
        return roles.stream()
                .anyMatch(role -> role.isActive() && normalize(role.getName()).equals(name));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record PermissionResolution(
            Set<String> inherited,
            Set<String> allowed,
            Set<String> denied,
            Set<String> effective
    ) {
        public PermissionResolution {
            inherited = Set.copyOf(inherited);
            allowed = Set.copyOf(allowed);
            denied = Set.copyOf(denied);
            effective = Set.copyOf(effective);
        }
    }
}
