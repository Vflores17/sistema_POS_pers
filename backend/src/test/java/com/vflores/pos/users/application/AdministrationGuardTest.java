package com.vflores.pos.users.application;

import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import com.vflores.pos.roles.domain.model.Permission;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.roles.domain.repository.RoleRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.model.UserStatus;
import com.vflores.pos.users.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
    private User nonAdminUser;

    @BeforeEach
    void setUp() {
        guard = new AdministrationGuard(userRepository, roleRepository);
        adminRole = Role.builder().id(UUID.randomUUID()).name("ADMIN").active(true).build();
        adminUser = User.builder().id(UUID.randomUUID()).status(UserStatus.ACTIVE).roles(Set.of(adminRole)).build();
        Role otherRole = Role.builder().id(UUID.randomUUID()).name("SELLER").active(true).build();
        nonAdminUser = User.builder().id(UUID.randomUUID()).status(UserStatus.ACTIVE).roles(Set.of(otherRole)).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User actor) {
        Set<GrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority("USER_ASSIGN_ROLE"),
                new SimpleGrantedAuthority("USER_ASSIGN_PERMISSION"),
                new SimpleGrantedAuthority("USER_CREATE"));
        var principal = new AuthenticatedUser(actor.getId(), "actor", "secret", "actor@example.com",
                "Actor", true, false, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
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

    @Test
    void nonAdminActorCannotAddAdminRoleToAnyUser() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        authenticateAs(nonAdminUser);
        when(userRepository.findById(nonAdminUser.getId())).thenReturn(Optional.of(nonAdminUser));

        assertThatThrownBy(() -> guard.requireAdminActorForAdminMembershipChange(
                nonAdminUser.getRoles(), Set.of(adminRole)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonAdminActorCannotRemoveAdminRole() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        authenticateAs(nonAdminUser);
        when(userRepository.findById(nonAdminUser.getId())).thenReturn(Optional.of(nonAdminUser));

        assertThatThrownBy(() -> guard.requireAdminActorForAdminMembershipChange(
                Set.of(adminRole), nonAdminUser.getRoles()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void grantedRoleAssignmentPermissionDoesNotGrantAdminMembershipPower() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        authenticateAs(nonAdminUser);
        when(userRepository.findById(nonAdminUser.getId())).thenReturn(Optional.of(nonAdminUser));

        assertThatThrownBy(() -> guard.requireAdminActorForAdminMembershipChange(
                Set.of(), Set.of(adminRole)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void activeAdminActorCanAddAndRemoveAdminRole() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        authenticateAs(adminUser);
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        assertThatCode(() -> guard.requireAdminActorForAdminMembershipChange(
                adminUser.getRoles(), Set.of())).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireAdminActorForAdminMembershipChange(
                Set.of(), Set.of(adminRole))).doesNotThrowAnyException();
    }

    @Test
    void unchangedMembershipIsAllowedWithoutRequiringAdminActor() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));

        assertThatCode(() -> guard.requireAdminActorForAdminMembershipChange(
                nonAdminUser.getRoles(), nonAdminUser.getRoles())).doesNotThrowAnyException();
    }
}
