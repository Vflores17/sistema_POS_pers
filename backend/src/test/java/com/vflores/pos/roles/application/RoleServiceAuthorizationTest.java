package com.vflores.pos.roles.application;

import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import com.vflores.pos.roles.api.dto.CreateRoleRequest;
import com.vflores.pos.roles.api.dto.RoleResponse;
import com.vflores.pos.roles.api.dto.UpdateRoleRequest;
import com.vflores.pos.roles.domain.model.Permission;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.roles.domain.repository.PermissionRepository;
import com.vflores.pos.roles.domain.repository.RoleRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.users.application.AdministrationGuard;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceAuthorizationTest {

    private static final UUID ACTOR_ID = UUID.fromString("b1b2c3d4-1111-1111-1111-111111111111");
    private static final UUID ADMIN_ROLE_ID = UUID.fromString("b1b2c3d4-2222-2222-2222-222222222222");
    private static final UUID OWN_ROLE_ID = UUID.fromString("b1b2c3d4-3333-3333-3333-333333333333");
    private static final UUID FOREIGN_ROLE_ID = UUID.fromString("b1b2c3d4-4444-4444-4444-444444444444");
    private static final UUID PERMISSION_ID = UUID.fromString("b1b2c3d4-5555-5555-5555-555555555555");
    private static final UUID SENSITIVE_PERMISSION_ID = UUID.fromString("b1b2c3d4-6666-6666-6666-666666666666");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;

    private AdministrationGuard guard;
    private RoleService service;
    private Role adminRole;
    private Role ownRole;
    private User nonAdminActor;
    private User adminActor;

    @BeforeEach
    void setUp() {
        guard = new AdministrationGuard(userRepository, roleRepository);
        service = new RoleService(roleRepository, permissionRepository, guard);
        adminRole = Role.builder().id(ADMIN_ROLE_ID).name("ADMIN").active(true).build();
        ownRole = Role.builder().id(OWN_ROLE_ID).name("SELLER").active(true).build();
        nonAdminActor = User.builder().id(ACTOR_ID).username("actor").email("actor@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(ownRole)).build();
        adminActor = User.builder().id(ACTOR_ID).username("admin").email("admin@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(adminRole)).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User actor) {
        Set<GrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority("ROLE_CREATE"),
                new SimpleGrantedAuthority("ROLE_UPDATE"),
                new SimpleGrantedAuthority("ROLE_DELETE"),
                new SimpleGrantedAuthority("ROLE_ASSIGN_PERMISSION"));
        var principal = new AuthenticatedUser(actor.getId(), actor.getUsername(), "secret",
                actor.getEmail(), "Actor", true, false, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private void stubActiveAdminActor() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(adminActor));
    }

    private void stubNonAdminActor() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(nonAdminActor));
    }

    private CreateRoleRequest createRequest(String name) {
        return new CreateRoleRequest(name, "A role", true);
    }

    @Test
    void createRoleByNonAdminIsDenied() {
        stubNonAdminActor();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.create(createRequest("SUPERVISOR")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createAdminEquivalentRoleByNonAdminIsDenied() {
        stubNonAdminActor();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.create(createRequest("ROOT")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateRoleByNonAdminIsDenied() {
        stubNonAdminActor();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.update(OWN_ROLE_ID, new UpdateRoleRequest("Updated", true)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteRoleByNonAdminIsDenied() {
        stubNonAdminActor();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.delete(FOREIGN_ROLE_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assignPermissionsToOwnRoleByNonAdminIsDenied() {
        stubNonAdminActor();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.assignPermissions(OWN_ROLE_ID, Set.of(PERMISSION_ID)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assignPermissionsToForeignRoleByNonAdminIsDenied() {
        stubNonAdminActor();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.assignPermissions(FOREIGN_ROLE_ID, Set.of(PERMISSION_ID)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assignPrivilegedPermissionsByNonAdminIsDenied() {
        stubNonAdminActor();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.assignPermissions(OWN_ROLE_ID, Set.of(SENSITIVE_PERMISSION_ID)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void touchingAdminRoleByNonAdminIsDenied() {
        stubNonAdminActor();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.assignPermissions(ADMIN_ROLE_ID, Set.of(PERMISSION_ID)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(ADMIN_ROLE_ID, new UpdateRoleRequest("Updated", false)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createRoleByAdminIsAllowed() {
        stubActiveAdminActor();
        when(roleRepository.existsByNameIgnoreCase("ROOT")).thenReturn(false);
        when(roleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAs(adminActor);

        RoleResponse response = service.create(createRequest("ROOT"));

        assertThat(response.name()).isEqualTo("ROOT");
    }

    @Test
    void updateRoleByAdminIsAllowed() {
        stubActiveAdminActor();
        when(roleRepository.findById(OWN_ROLE_ID)).thenReturn(Optional.of(ownRole));
        when(roleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAs(adminActor);

        assertThatCode(() -> service.update(OWN_ROLE_ID, new UpdateRoleRequest("Updated", true)))
                .doesNotThrowAnyException();
    }

    @Test
    void deleteRoleByAdminIsAllowed() {
        stubActiveAdminActor();
        when(roleRepository.findById(FOREIGN_ROLE_ID)).thenReturn(Optional.of(ownRole));
        authenticateAs(adminActor);

        assertThatCode(() -> service.delete(FOREIGN_ROLE_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void assignPermissionsByAdminIsAllowed() {
        Permission permission = Permission.builder()
                .id(PERMISSION_ID).code("REPORT_READ").module("REPORTS").description("View reports").build();
        stubActiveAdminActor();
        when(roleRepository.findById(OWN_ROLE_ID)).thenReturn(Optional.of(ownRole));
        when(permissionRepository.findAllById(any())).thenReturn(List.of(permission));
        when(roleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAs(adminActor);

        assertThatCode(() -> service.assignPermissions(OWN_ROLE_ID, Set.of(PERMISSION_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    void adminCannotStripEssentialPermissionsFromAdminRole() {
        Permission permission = Permission.builder()
                .id(PERMISSION_ID).code("REPORT_READ").module("REPORTS").description("View reports").build();
        stubActiveAdminActor();
        when(roleRepository.findById(ADMIN_ROLE_ID)).thenReturn(Optional.of(adminRole));
        when(permissionRepository.findAllById(any())).thenReturn(List.of(permission));
        authenticateAs(adminActor);

        assertThatThrownBy(() -> service.assignPermissions(ADMIN_ROLE_ID, Set.of(PERMISSION_ID)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void adminCanReassignAdminRoleWhenEssentialsPreserved() {
        List<String> essentialCodes = List.of(
                "USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DELETE",
                "USER_ASSIGN_ROLE", "USER_ASSIGN_PERMISSION",
                "ROLE_READ", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_DELETE", "ROLE_ASSIGN_PERMISSION",
                "PERMISSION_READ", "PERMISSION_CREATE", "PERMISSION_UPDATE", "PERMISSION_DELETE");
        List<Permission> essentials = essentialCodes.stream()
                .map(code -> Permission.builder()
                        .id(UUID.nameUUIDFromBytes(code.getBytes())).code(code).module("ADMIN").build())
                .toList();
        Set<UUID> permissionIds = essentials.stream().map(Permission::getId).collect(java.util.stream.Collectors.toSet());
        stubActiveAdminActor();
        when(roleRepository.findById(ADMIN_ROLE_ID)).thenReturn(Optional.of(adminRole));
        when(permissionRepository.findAllById(any())).thenReturn(essentials);
        when(roleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAs(adminActor);

        assertThatCode(() -> service.assignPermissions(ADMIN_ROLE_ID, permissionIds))
                .doesNotThrowAnyException();
    }
}