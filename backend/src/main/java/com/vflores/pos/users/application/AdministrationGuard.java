package com.vflores.pos.users.application;

import com.vflores.pos.roles.domain.model.Permission;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.roles.domain.repository.RoleRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.model.UserStatus;
import com.vflores.pos.users.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdministrationGuard {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final Set<String> ESSENTIAL_ADMIN_PERMISSIONS = Set.of(
            "USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DELETE",
            "USER_ASSIGN_ROLE", "USER_ASSIGN_PERMISSION",
            "ROLE_READ", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_DELETE", "ROLE_ASSIGN_PERMISSION",
            "PERMISSION_READ", "PERMISSION_CREATE", "PERMISSION_UPDATE", "PERMISSION_DELETE"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAdministrationAfterUserChange(User user, UserStatus newStatus, Set<Role> newRoles) {
        Role adminRole = lockAdminRole();
        boolean currentlyActiveAdmin = isActiveAdmin(user, adminRole);
        boolean remainsActiveAdmin = newStatus == UserStatus.ACTIVE
                && newRoles.stream().anyMatch(role -> role.getId().equals(adminRole.getId()) && role.isActive());
        if (currentlyActiveAdmin && !remainsActiveAdmin) {
            requireAnotherActiveAdmin(adminRole);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAdministrationAfterUserDeletion(User user) {
        Role adminRole = lockAdminRole();
        if (isActiveAdmin(user, adminRole)) {
            requireAnotherActiveAdmin(adminRole);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAdministrationAfterRoleUpdate(Role role, boolean newActive) {
        if (!isAdminRole(role) || newActive) {
            return;
        }
        Role adminRole = lockAdminRole();
        if (activeAdminCount(adminRole) > 0) {
            throw conflict();
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireEssentialAdminPermissions(Role role, Set<Permission> permissions) {
        if (!isAdminRole(role)) {
            return;
        }
        lockAdminRole();
        Set<String> codes = permissions.stream()
                .map(Permission::getCode)
                .map(this::normalize)
                .collect(java.util.stream.Collectors.toSet());
        if (!codes.containsAll(ESSENTIAL_ADMIN_PERMISSIONS)) {
            throw new ConflictException("Essential ADMIN permissions cannot be removed");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requirePermissionIsNotEssential(String permissionCode) {
        if (ESSENTIAL_ADMIN_PERMISSIONS.contains(normalize(permissionCode))) {
            throw new ConflictException("Essential ADMIN permissions cannot be deleted");
        }
    }

    private Role lockAdminRole() {
        return roleRepository.findByNameForUpdate(ADMIN_ROLE)
                .orElseThrow(() -> new ConflictException("An active ADMIN role is required"));
    }

    private boolean isActiveAdmin(User user, Role adminRole) {
        return user.getStatus() == UserStatus.ACTIVE
                && adminRole.isActive()
                && user.getRoles().stream().anyMatch(role -> role.getId().equals(adminRole.getId()));
    }

    private boolean isAdminRole(Role role) {
        return ADMIN_ROLE.equals(normalize(role.getName()));
    }

    private long activeAdminCount(Role adminRole) {
        return adminRole.isActive()
                ? userRepository.countByStatusAndRolesId(UserStatus.ACTIVE, adminRole.getId())
                : 0;
    }

    private void requireAnotherActiveAdmin(Role adminRole) {
        if (activeAdminCount(adminRole) <= 1) {
            throw conflict();
        }
    }

    private ConflictException conflict() {
        return new ConflictException("The last active administrator must be preserved");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
