package com.vflores.pos.users.application;

import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import com.vflores.pos.auth.domain.repository.RefreshTokenRepository;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.roles.domain.repository.RoleRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.users.api.dto.UpdateUserRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceAdminLifecycleTest {

    private static final UUID ADMIN_ACTOR_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001");
    private static final UUID NON_ADMIN_ACTOR_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000002");
    private static final UUID TARGET_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000003");
    private static final UUID SELF_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000004");
    private static final UUID ADMIN_ROLE_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000010");
    private static final UUID OTHER_ROLE_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000011");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private AdministrationGuard guard;
    private UserService service;
    private Role adminRole;
    private Role otherRole;
    private User adminActor;
    private User nonAdminActor;

    @BeforeEach
    void setUp() {
        guard = new AdministrationGuard(userRepository, roleRepository);
        service = new UserService(userRepository, roleRepository, passwordEncoder, guard, refreshTokenRepository);
        adminRole = Role.builder().id(ADMIN_ROLE_ID).name("ADMIN").active(true).build();
        otherRole = Role.builder().id(OTHER_ROLE_ID).name("SELLER").active(true).build();
        adminActor = User.builder().id(ADMIN_ACTOR_ID).username("admin").email("admin@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(adminRole)).build();
        nonAdminActor = User.builder().id(NON_ADMIN_ACTOR_ID).username("actor").email("actor@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(otherRole)).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User actor) {
        Set<GrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority("USER_UPDATE"),
                new SimpleGrantedAuthority("USER_ASSIGN_ROLE"));
        var principal = new AuthenticatedUser(actor.getId(), actor.getUsername(), "secret",
                actor.getEmail(), "Actor", true, false, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private UpdateUserRequest updateRequest(String email, UserStatus status, Set<UUID> roleIds) {
        return new UpdateUserRequest(email, "Target User", status, roleIds);
    }

    private User target(boolean admin, UserStatus status) {
        Set<Role> roles = admin ? Set.of(adminRole) : Set.of(otherRole);
        return User.builder().id(TARGET_ID).username("target").email("target@example.com")
                .passwordHash("hash").status(status).roles(roles).build();
    }

    private void stubUniqueGuards() {
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
    }

    private void stubAdminRoleLookup() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllById(Set.of(ADMIN_ROLE_ID))).thenReturn(List.of(adminRole));
    }

    @Test
    void deactivatedAdminCannotBeReactivatedByNonAdmin() {
        User target = target(true, UserStatus.INACTIVE);
        stubAdminRoleLookup();
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(NON_ADMIN_ACTOR_ID)).thenReturn(Optional.of(nonAdminActor));
        stubUniqueGuards();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.update(TARGET_ID,
                updateRequest("target@example.com", UserStatus.ACTIVE, Set.of(ADMIN_ROLE_ID))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonAdminCannotChangeEmailOfAdminTarget() {
        User target = target(true, UserStatus.ACTIVE);
        stubAdminRoleLookup();
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(NON_ADMIN_ACTOR_ID)).thenReturn(Optional.of(nonAdminActor));
        stubUniqueGuards();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.update(TARGET_ID,
                updateRequest("different@example.com", UserStatus.ACTIVE, Set.of(ADMIN_ROLE_ID))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonAdminCannotDeactivateNonLastActiveAdmin() {
        User target = target(true, UserStatus.ACTIVE);
        stubAdminRoleLookup();
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(NON_ADMIN_ACTOR_ID)).thenReturn(Optional.of(nonAdminActor));
        stubUniqueGuards();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.update(TARGET_ID,
                updateRequest("target@example.com", UserStatus.INACTIVE, Set.of(ADMIN_ROLE_ID))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonAdminCannotChangeStatusOfAdminTarget() {
        User target = target(true, UserStatus.ACTIVE);
        stubAdminRoleLookup();
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(NON_ADMIN_ACTOR_ID)).thenReturn(Optional.of(nonAdminActor));
        stubUniqueGuards();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.update(TARGET_ID,
                updateRequest("target@example.com", UserStatus.BLOCKED, Set.of(ADMIN_ROLE_ID))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminCanPerformAllowedAdminLifecycleChanges() {
        User target = target(true, UserStatus.ACTIVE);
        stubAdminRoleLookup();
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ADMIN_ACTOR_ID)).thenReturn(Optional.of(adminActor));
        when(userRepository.countByStatusAndRolesId(UserStatus.ACTIVE, ADMIN_ROLE_ID)).thenReturn(2L);
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubUniqueGuards();
        authenticateAs(adminActor);

        assertThatCode(() -> service.update(TARGET_ID,
                updateRequest("target@example.com", UserStatus.INACTIVE, Set.of(ADMIN_ROLE_ID))))
                .doesNotThrowAnyException();
        verify(refreshTokenRepository).revokeAllByUserId(TARGET_ID);
    }

    @Test
    void selfServiceCannotBypassAdministrativeProtection() {
        User deactivatedSelf = User.builder().id(SELF_ID).username("admin").email("self@example.com")
                .passwordHash("hash").status(UserStatus.INACTIVE).roles(Set.of(adminRole)).build();
        stubAdminRoleLookup();
        when(userRepository.findById(SELF_ID)).thenReturn(Optional.of(deactivatedSelf));
        stubUniqueGuards();
        authenticateAs(deactivatedSelf);

        assertThatThrownBy(() -> service.update(SELF_ID,
                updateRequest("self@example.com", UserStatus.ACTIVE, Set.of(ADMIN_ROLE_ID))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void lastAdminProtectionStillWorks() {
        User target = target(true, UserStatus.ACTIVE);
        stubAdminRoleLookup();
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ADMIN_ACTOR_ID)).thenReturn(Optional.of(adminActor));
        when(userRepository.countByStatusAndRolesId(UserStatus.ACTIVE, ADMIN_ROLE_ID)).thenReturn(1L);
        stubUniqueGuards();
        authenticateAs(adminActor);

        assertThatThrownBy(() -> service.update(TARGET_ID,
                updateRequest("target@example.com", UserStatus.INACTIVE, Set.of(ADMIN_ROLE_ID))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void nonAdminCanStillUpdateNonAdminTarget() {
        User target = target(false, UserStatus.ACTIVE);
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllById(Set.of(OTHER_ROLE_ID))).thenReturn(List.of(otherRole));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubUniqueGuards();
        authenticateAs(nonAdminActor);

        assertThatCode(() -> service.update(TARGET_ID,
                updateRequest("target@example.com", UserStatus.INACTIVE, Set.of(OTHER_ROLE_ID))))
                .doesNotThrowAnyException();
    }
}