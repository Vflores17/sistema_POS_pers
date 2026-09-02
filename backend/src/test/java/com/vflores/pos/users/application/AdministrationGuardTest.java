package com.vflores.pos.users.application;

import com.vflores.pos.roles.domain.model.Permission;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.roles.domain.repository.RoleRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.model.UserStatus;
import com.vflores.pos.users.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdministrationGuardTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;

    private AdministrationGuard guard;
    private Role adminRole;
    private User adminUser;

    @BeforeEach
    void setUp() {
        guard = new AdministrationGuard(userRepository, roleRepository);
        adminRole = Role.builder().id(UUID.randomUUID()).name("ADMIN").active(true).build();
        adminUser = User.builder().id(UUID.randomUUID()).status(UserStatus.ACTIVE).roles(Set.of(adminRole)).build();
    }

    @Test
    void lastActiveAdminCannotBeBlockedOrLoseAdminRole() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.countByStatusAndRolesId(UserStatus.ACTIVE, adminRole.getId())).thenReturn(1L);

        assertThatThrownBy(() -> guard.requireAdministrationAfterUserChange(
                adminUser, UserStatus.BLOCKED, Set.of(adminRole)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> guard.requireAdministrationAfterUserChange(
                adminUser, UserStatus.ACTIVE, Set.of()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void adminCanChangeWhenAnotherActiveAdminRemains() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.countByStatusAndRolesId(UserStatus.ACTIVE, adminRole.getId())).thenReturn(2L);

        assertThatCode(() -> guard.requireAdministrationAfterUserDeletion(adminUser)).doesNotThrowAnyException();
    }

    @Test
    void activeAdminRoleCannotBeDisabled() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.countByStatusAndRolesId(UserStatus.ACTIVE, adminRole.getId())).thenReturn(1L);

        assertThatThrownBy(() -> guard.requireAdministrationAfterRoleUpdate(adminRole, false))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void essentialPermissionsCannotBeRemovedFromAdminRole() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        Set<Permission> incomplete = Set.of(Permission.builder().code("USER_READ").build());

        assertThatThrownBy(() -> guard.requireEssentialAdminPermissions(adminRole, incomplete))
                .isInstanceOf(ConflictException.class);
    }
}
