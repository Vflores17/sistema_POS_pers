package com.vflores.pos.users.application;

import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.roles.domain.repository.RoleRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.users.api.dto.CreateUserRequest;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceRoleEscalationTest {

    private static final UUID ACTOR_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000002");
    private static final UUID ADMIN_ROLE_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000010");
    private static final UUID OTHER_ROLE_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000011");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdministrationGuard guard;
    private UserService service;
    private Role adminRole;
    private Role otherRole;
    private User nonAdminActor;
    private User adminActor;
    private User target;

    @BeforeEach
    void setUp() {
        guard = new AdministrationGuard(userRepository, roleRepository);
        service = new UserService(userRepository, roleRepository, passwordEncoder, guard);
        adminRole = Role.builder().id(ADMIN_ROLE_ID).name("ADMIN").active(true).build();
        otherRole = Role.builder().id(OTHER_ROLE_ID).name("SELLER").active(true).build();
        nonAdminActor = User.builder().id(ACTOR_ID).username("actor").email("actor@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(otherRole)).build();
        adminActor = User.builder().id(ACTOR_ID).username("admin").email("admin@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(adminRole)).build();
        target = User.builder().id(TARGET_ID).username("target").email("target@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(otherRole)).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User actor) {
        Set<GrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority("USER_ASSIGN_ROLE"),
                new SimpleGrantedAuthority("USER_ASSIGN_PERMISSION"),
                new SimpleGrantedAuthority("USER_CREATE"),
                new SimpleGrantedAuthority("USER_UPDATE"));
        var principal = new AuthenticatedUser(actor.getId(), actor.getUsername(), "secret",
                actor.getEmail(), "Actor", true, false, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private CreateUserRequest createRequest(Set<UUID> roleIds) {
        return new CreateUserRequest("newuser", "newuser@example.com", "New User",
                "password123", UserStatus.ACTIVE, roleIds);
    }

    private UpdateUserRequest updateRequest(Set<UUID> roleIds) {
        return new UpdateUserRequest("target@example.com", "Target User", UserStatus.ACTIVE, roleIds);
    }

    private void stubUniqueGuards() {
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
    }

    @Test
    void assignRolesAddingAdminByNonAdminActorIsDenied() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        mockAdminRoleLookup();
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(nonAdminActor));
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.assignRoles(TARGET_ID, Set.of(ADMIN_ROLE_ID)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateAddingAdminByNonAdminActorIsDenied() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        mockAdminRoleLookup();
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(nonAdminActor));
        stubUniqueGuards();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.update(TARGET_ID, updateRequest(Set.of(ADMIN_ROLE_ID))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createWithAdminByNonAdminActorIsDenied() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        mockAdminRoleLookup();
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(nonAdminActor));
        stubUniqueGuards();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.create(createRequest(Set.of(ADMIN_ROLE_ID))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createWithoutAdminByNonAdminActorIsAllowed() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllById(Set.of(OTHER_ROLE_ID))).thenReturn(List.of(otherRole));
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubUniqueGuards();
        authenticateAs(nonAdminActor);

        assertThatCode(() -> service.create(createRequest(Set.of(OTHER_ROLE_ID))))
                .doesNotThrowAnyException();
    }

    @Test
    void assignRolesNormalRoleByNonAdminActorIsAllowed() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllById(Set.of(OTHER_ROLE_ID))).thenReturn(List.of(otherRole));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAs(nonAdminActor);

        assertThatCode(() -> service.assignRoles(TARGET_ID, Set.of(OTHER_ROLE_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    void assignRolesAddingAdminByAdminActorIsAllowed() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllById(Set.of(ADMIN_ROLE_ID))).thenReturn(List.of(adminRole));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(adminActor));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAs(adminActor);

        assertThatCode(() -> service.assignRoles(TARGET_ID, Set.of(ADMIN_ROLE_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    void adminActorCanRemoveAdminRoleWhenAnotherActiveAdminRemains() {
        User targetAdmin = User.builder().id(TARGET_ID).username("target").email("target@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(adminRole)).build();
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllById(Set.of(OTHER_ROLE_ID))).thenReturn(List.of(otherRole));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(targetAdmin));
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(adminActor));
        when(userRepository.countByStatusAndRolesId(UserStatus.ACTIVE, adminRole.getId())).thenReturn(2L);
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAs(adminActor);

        assertThatCode(() -> service.assignRoles(TARGET_ID, Set.of(OTHER_ROLE_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    void removingAdminRoleFromLastActiveAdminRemainsBlocked() {
        User targetAdmin = User.builder().id(TARGET_ID).username("target").email("target@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(adminRole)).build();
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllById(Set.of(OTHER_ROLE_ID))).thenReturn(List.of(otherRole));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(targetAdmin));
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(adminActor));
        when(userRepository.countByStatusAndRolesId(UserStatus.ACTIVE, adminRole.getId())).thenReturn(1L);
        authenticateAs(adminActor);

        assertThatThrownBy(() -> service.assignRoles(TARGET_ID, Set.of(OTHER_ROLE_ID)))
                .isInstanceOf(ConflictException.class);
    }

    private void mockAdminRoleLookup() {
        when(roleRepository.findAllById(Set.of(ADMIN_ROLE_ID))).thenReturn(List.of(adminRole));
    }
}