package com.vflores.pos.users.application;

import com.vflores.pos.auth.application.EffectivePermissionService;
import com.vflores.pos.auth.application.EffectivePermissionService.PermissionResolution;
import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import com.vflores.pos.roles.domain.model.Permission;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.roles.domain.repository.PermissionRepository;
import com.vflores.pos.users.api.dto.PermissionOverrideItemRequest;
import com.vflores.pos.users.domain.model.PermissionOverrideEffect;
import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.model.UserStatus;
import com.vflores.pos.users.domain.repository.UserPermissionOverrideRepository;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPermissionServiceAuthorizationTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private UserPermissionOverrideRepository overrideRepository;
    @Mock
    private EffectivePermissionService effectivePermissionService;
    @Mock
    private AdministrationGuard administrationGuard;

    private UserPermissionService service;
    private User actor;
    private User otherUser;
    private User admin;
    private UUID permissionId;

    @BeforeEach
    void setUp() {
        service = new UserPermissionService(
                userRepository, permissionRepository, overrideRepository, effectivePermissionService, administrationGuard);
        Role sellerRole = Role.builder().id(UUID.randomUUID()).name("SELLER").active(true).build();
        Role adminRole = Role.builder().id(UUID.randomUUID()).name("ADMIN").active(true).build();
        actor = User.builder().id(UUID.randomUUID()).username("actor").email("actor@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(sellerRole)).build();
        otherUser = User.builder().id(UUID.randomUUID()).username("other").email("other@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(sellerRole)).build();
        admin = User.builder().id(UUID.randomUUID()).username("admin").email("admin@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(adminRole)).build();
        permissionId = UUID.randomUUID();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        Set<GrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority("USER_ASSIGN_PERMISSION"));
        var principal = new AuthenticatedUser(user.getId(), user.getUsername(), "secret", user.getEmail(),
                "Actor", true, false, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private void stubActor() {
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
    }

    private void stubActorAndOther() {
        stubActor();
        when(userRepository.findById(otherUser.getId())).thenReturn(Optional.of(otherUser));
    }

    private void stubActorAndAdmin() {
        stubActor();
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
    }

    private void stubAdmin() {
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
    }

    private void stubAdminAndOther() {
        stubAdmin();
        when(userRepository.findById(otherUser.getId())).thenReturn(Optional.of(otherUser));
    }

    private Permission permission(String code) {
        return Permission.builder().id(permissionId).code(code).module("TEST").build();
    }

    private void stubPermission(String code) {
        when(permissionRepository.findAllById(anySet())).thenReturn(List.of(permission(code)));
    }

    private void stubEmptyResolution() {
        when(effectivePermissionService.resolve(any())).thenReturn(
                new PermissionResolution(Set.of(), Set.of(), Set.of(), Set.of()));
    }

    private List<PermissionOverrideItemRequest> overrides(PermissionOverrideEffect effect) {
        return List.of(new PermissionOverrideItemRequest(permissionId, effect));
    }

    private void assertSelfAllowDenied(String code) {
        authenticateAs(actor);
        stubActor();
        stubPermission(code);
        stubEmptyResolution();
        when(administrationGuard.actorIsActiveAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.replace(actor.getId(), overrides(PermissionOverrideEffect.ALLOW)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonAdminCannotSelfAllowSaleCancel() {
        assertSelfAllowDenied("SALE_CANCEL");
    }

    @Test
    void nonAdminCannotSelfAllowSalePriceOverride() {
        assertSelfAllowDenied("SALE_PRICE_OVERRIDE");
    }

    @Test
    void nonAdminCannotSelfAllowRouteUpdate() {
        assertSelfAllowDenied("ROUTE_UPDATE");
    }

    @Test
    void nonAdminCannotSelfAllowUserDelete() {
        assertSelfAllowDenied("USER_DELETE");
    }

    @Test
    void nonAdminCannotSelfAllowRoleDelete() {
        assertSelfAllowDenied("ROLE_DELETE");
    }

    @Test
    void nonAdminCannotSelfAllowUserAssignRole() {
        assertSelfAllowDenied("USER_ASSIGN_ROLE");
    }

    @Test
    void nonAdminCannotSelfAllowUserAssignPermission() {
        assertSelfAllowDenied("USER_ASSIGN_PERMISSION");
    }

    @Test
    void nonAdminCannotSelfAllowRoleAssignPermission() {
        assertSelfAllowDenied("ROLE_ASSIGN_PERMISSION");
    }

    @Test
    void nonAdminCanSelfAllowPermissionAlreadyHeld() {
        Permission permission = permission("SALE_CANCEL");
        authenticateAs(actor);
        stubActor();
        when(permissionRepository.findAllById(anySet())).thenReturn(List.of(permission));
        when(effectivePermissionService.resolve(actor)).thenReturn(
                new PermissionResolution(
                        Set.of("SALE_CANCEL"), Set.of(), Set.of(), Set.of("SALE_CANCEL")));
        when(administrationGuard.actorIsActiveAdmin()).thenReturn(false);

        assertThatCode(() -> service.replace(actor.getId(), overrides(PermissionOverrideEffect.ALLOW)))
                .doesNotThrowAnyException();
    }

    @Test
    void nonAdminCannotModifyAnotherUsersOverrides() {
        authenticateAs(actor);
        stubActorAndOther();
        stubPermission("SALE_CANCEL");
        when(administrationGuard.actorIsActiveAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.replace(otherUser.getId(), overrides(PermissionOverrideEffect.DENY)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonAdminCannotClearAnotherUsersOverrides() {
        authenticateAs(actor);
        stubActorAndOther();
        when(administrationGuard.actorIsActiveAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.clear(otherUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonAdminCannotModifyAdminUsersOverrides() {
        authenticateAs(actor);
        stubActorAndAdmin();
        stubPermission("SALE_CANCEL");
        when(administrationGuard.actorIsActiveAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.replace(admin.getId(), overrides(PermissionOverrideEffect.DENY)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminCanReplaceOwnOverridesWithAllow() {
        authenticateAs(admin);
        stubAdmin();
        stubPermission("SALE_CANCEL");
        stubEmptyResolution();
        when(administrationGuard.actorIsActiveAdmin()).thenReturn(true);

        assertThatCode(() -> service.replace(admin.getId(), overrides(PermissionOverrideEffect.ALLOW)))
                .doesNotThrowAnyException();
    }

    @Test
    void adminCanReplaceAnotherUsersOverridesWithDeny() {
        authenticateAs(admin);
        stubAdminAndOther();
        stubPermission("SALE_CANCEL");
        stubEmptyResolution();
        when(administrationGuard.actorIsActiveAdmin()).thenReturn(true);

        assertThatCode(() -> service.replace(otherUser.getId(), overrides(PermissionOverrideEffect.DENY)))
                .doesNotThrowAnyException();
    }

    @Test
    void adminCanReplaceAnotherUsersOverridesWithAllow() {
        authenticateAs(admin);
        stubAdminAndOther();
        stubPermission("SALE_CANCEL");
        stubEmptyResolution();
        when(administrationGuard.actorIsActiveAdmin()).thenReturn(true);

        assertThatCode(() -> service.replace(otherUser.getId(), overrides(PermissionOverrideEffect.ALLOW)))
                .doesNotThrowAnyException();
    }

    @Test
    void adminCanClearAnotherUsersOverrides() {
        authenticateAs(admin);
        stubAdminAndOther();
        stubEmptyResolution();
        when(administrationGuard.actorIsActiveAdmin()).thenReturn(true);

        assertThatCode(() -> service.clear(otherUser.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void nonAdminCanSelfDeny() {
        authenticateAs(actor);
        stubActor();
        stubPermission("SALE_CANCEL");
        stubEmptyResolution();
        when(administrationGuard.actorIsActiveAdmin()).thenReturn(false);

        assertThatCode(() -> service.replace(actor.getId(), overrides(PermissionOverrideEffect.DENY)))
                .doesNotThrowAnyException();
    }
}