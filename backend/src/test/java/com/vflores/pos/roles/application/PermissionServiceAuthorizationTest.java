package com.vflores.pos.roles.application;

import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import com.vflores.pos.roles.api.dto.CreatePermissionRequest;
import com.vflores.pos.roles.api.dto.PermissionResponse;
import com.vflores.pos.roles.api.dto.UpdatePermissionRequest;
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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceAuthorizationTest {

    private static final UUID ACTOR_ID = UUID.fromString("c1b2c3d4-1111-1111-1111-111111111111");
    private static final UUID ADMIN_ROLE_ID = UUID.fromString("c1b2c3d4-2222-2222-2222-222222222222");
    private static final UUID PERMISSION_ID = UUID.fromString("c1b2c3d4-3333-3333-3333-333333333333");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;

    private AdministrationGuard guard;
    private PermissionService service;
    private Role adminRole;
    private User nonAdminActor;
    private User adminActor;

    @BeforeEach
    void setUp() {
        guard = new AdministrationGuard(userRepository, roleRepository);
        service = new PermissionService(permissionRepository, guard);
        adminRole = Role.builder().id(ADMIN_ROLE_ID).name("ADMIN").active(true).build();
        nonAdminActor = User.builder().id(ACTOR_ID).username("actor").email("actor@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of()).build();
        adminActor = User.builder().id(ACTOR_ID).username("admin").email("admin@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(adminRole)).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User actor) {
        Set<GrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority("PERMISSION_CREATE"),
                new SimpleGrantedAuthority("PERMISSION_UPDATE"),
                new SimpleGrantedAuthority("PERMISSION_DELETE"));
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

    private CreatePermissionRequest createRequest() {
        return new CreatePermissionRequest("REPORT_READ", "REPORTS", "View reports");
    }

    @Test
    void createPermissionByNonAdminIsDenied() {
        stubNonAdminActor();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updatingPermissionByNonAdminIsDenied() {
        stubNonAdminActor();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.update(PERMISSION_ID, new UpdatePermissionRequest("REPORTS", "Updated")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deletePermissionByNonAdminIsDenied() {
        stubNonAdminActor();
        authenticateAs(nonAdminActor);

        assertThatThrownBy(() -> service.delete(PERMISSION_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createPermissionByAdminIsAllowed() {
        stubActiveAdminActor();
        when(permissionRepository.existsByCode("REPORT_READ")).thenReturn(false);
        when(permissionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAs(adminActor);

        PermissionResponse response = service.create(createRequest());

        assertThat(response.code()).isEqualTo("REPORT_READ");
    }

    @Test
    void updatePermissionByAdminIsAllowed() {
        Permission permission = Permission.builder()
                .id(PERMISSION_ID).code("REPORT_READ").module("REPORTS").description("View reports").build();
        stubActiveAdminActor();
        when(permissionRepository.findById(PERMISSION_ID)).thenReturn(Optional.of(permission));
        when(permissionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAs(adminActor);

        assertThatCode(() -> service.update(PERMISSION_ID, new UpdatePermissionRequest("REPORTS", "Updated")))
                .doesNotThrowAnyException();
    }

    @Test
    void deletePermissionByAdminIsAllowed() {
        Permission permission = Permission.builder()
                .id(PERMISSION_ID).code("REPORT_READ").module("REPORTS").description("View reports").build();
        stubActiveAdminActor();
        when(permissionRepository.findById(PERMISSION_ID)).thenReturn(Optional.of(permission));
        authenticateAs(adminActor);

        assertThatCode(() -> service.delete(PERMISSION_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void deleteEssentialPermissionByAdminIsForbidden() {
        Permission essential = Permission.builder()
                .id(PERMISSION_ID).code("USER_READ").module("ADMIN").description("View users").build();
        stubActiveAdminActor();
        when(permissionRepository.findById(PERMISSION_ID)).thenReturn(Optional.of(essential));
        authenticateAs(adminActor);

        assertThatThrownBy(() -> service.delete(PERMISSION_ID))
                .isInstanceOf(ConflictException.class);
    }
}