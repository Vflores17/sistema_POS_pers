package com.vflores.pos.auth.application;

import com.vflores.pos.roles.domain.model.Permission;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.roles.domain.repository.PermissionRepository;
import com.vflores.pos.users.domain.model.PermissionOverrideEffect;
import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.model.UserPermissionOverride;
import com.vflores.pos.users.domain.repository.UserPermissionOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EffectivePermissionServiceTest {

    @Mock
    private UserPermissionOverrideRepository overrideRepository;

    @Mock
    private PermissionRepository permissionRepository;

    private EffectivePermissionService service;

    @BeforeEach
    void setUp() {
        service = new EffectivePermissionService(overrideRepository, permissionRepository);
    }

    @Test
    void expandsLegacySaleWritePermission() {
        User user = userWithRole("VENDEDOR", permission("SALE_WRITE"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        Set<String> effective = service.resolve(user).effective();

        assertThat(effective).contains(
                "SALE_WRITE", "SALE_CREATE", "SALE_UPDATE", "SALE_DELETE", "SALE_CANCEL"
        );
    }

    @Test
    void denyRemovesPermissionExpandedFromLegacyWrite() {
        Permission saleUpdate = permission("SALE_UPDATE");
        User user = userWithRole("VENDEDOR", permission("SALE_WRITE"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of(
                override(user, saleUpdate, PermissionOverrideEffect.DENY)
        ));

        Set<String> effective = service.resolve(user).effective();

        assertThat(effective).contains("SALE_CREATE", "SALE_DELETE", "SALE_CANCEL");
        assertThat(effective).doesNotContain("SALE_UPDATE");
    }

    @Test
    void allowAddsPermissionNotInheritedFromRoles() {
        Permission productRead = permission("PRODUCT_READ");
        User user = userWithRole("CONSULTA");
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of(
                override(user, productRead, PermissionOverrideEffect.ALLOW)
        ));

        assertThat(service.resolve(user).effective()).containsExactly("PRODUCT_READ");
    }

    @Test
    void adminReceivesEntireCatalogAndIgnoresDeny() {
        Permission saleUpdate = permission("SALE_UPDATE");
        Permission saleCancel = permission("SALE_CANCEL");
        Permission saleCreate = permission("SALE_CREATE");
        Permission saleDelete = permission("SALE_DELETE");
        Permission userDelete = permission("USER_DELETE");
        Permission userAssignPermission = permission("USER_ASSIGN_PERMISSION");
        User user = userWithRole("ADMIN");
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of(
                override(user, saleUpdate, PermissionOverrideEffect.DENY)
        ));
        when(permissionRepository.findAll()).thenReturn(List.of(
                saleUpdate, saleCancel, saleCreate, saleDelete, userDelete, userAssignPermission
        ));

        EffectivePermissionService.PermissionResolution resolution = service.resolve(user);

        assertThat(resolution.effective()).contains(
                "SALE_CREATE", "SALE_UPDATE", "SALE_DELETE", "SALE_CANCEL", "USER_DELETE",
                "USER_ASSIGN_PERMISSION"
        );
        assertThat(resolution.denied()).isEmpty();
    }

    @Test
    void expandsLegacyClientWritePermission() {
        User user = userWithRole("VENDEDOR", permission("CLIENT_READ"), permission("CLIENT_WRITE"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        assertThat(service.resolve(user).effective()).contains(
                "CLIENT_READ", "CLIENT_WRITE", "CLIENT_CREATE", "CLIENT_UPDATE", "CLIENT_DELETE"
        );
    }

    @Test
    void clientUpdateDenyWinsOverLegacyClientWrite() {
        Permission clientUpdate = permission("CLIENT_UPDATE");
        User user = userWithRole("VENDEDOR", permission("CLIENT_READ"), permission("CLIENT_WRITE"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of(
                override(user, clientUpdate, PermissionOverrideEffect.DENY)
        ));

        Set<String> effective = service.resolve(user).effective();

        assertThat(effective).contains("CLIENT_READ", "CLIENT_CREATE", "CLIENT_DELETE");
        assertThat(effective).doesNotContain("CLIENT_UPDATE");
    }

    @Test
    void clientUpdateAllowWorksWithoutInheritedWritePermission() {
        Permission clientUpdate = permission("CLIENT_UPDATE");
        User user = userWithRole("CONSULTA", permission("CLIENT_READ"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of(
                override(user, clientUpdate, PermissionOverrideEffect.ALLOW)
        ));

        assertThat(service.resolve(user).effective()).contains("CLIENT_READ", "CLIENT_UPDATE");
    }

    @Test
    void readOnlyClientUserHasNoMutationPermissions() {
        User user = userWithRole("CONSULTA", permission("CLIENT_READ"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        assertThat(service.resolve(user).effective())
                .containsExactly("CLIENT_READ")
                .doesNotContain("CLIENT_CREATE", "CLIENT_UPDATE", "CLIENT_DELETE");
    }

    @Test
    void expandsLegacyProductWritePermission() {
        assertLegacyWriteExpansion(
                "PRODUCT_WRITE", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DELETE"
        );
    }

    @Test
    void productUpdateDenyWinsOverLegacyProductWrite() {
        assertDenyWinsOverLegacyWrite(
                "PRODUCT_WRITE", "PRODUCT_UPDATE", "PRODUCT_CREATE", "PRODUCT_DELETE"
        );
    }

    @Test
    void productUpdateAllowWorksWithoutInheritedWritePermission() {
        assertAllowAddsPermission("PRODUCT_READ", "PRODUCT_UPDATE");
    }

    @Test
    void productReadDoesNotImplyPriceRead() {
        User user = userWithRole("CONSULTA", permission("PRODUCT_READ"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        assertThat(service.resolve(user).effective()).contains("PRODUCT_READ").doesNotContain("PRICE_READ");
    }

    @Test
    void expandsLegacyPriceWritePermission() {
        assertLegacyWriteExpansion("PRICE_WRITE", "PRICE_CREATE", "PRICE_UPDATE", "PRICE_DELETE");
    }

    @Test
    void priceUpdateDenyWinsOverLegacyPriceWrite() {
        assertDenyWinsOverLegacyWrite("PRICE_WRITE", "PRICE_UPDATE", "PRICE_CREATE", "PRICE_DELETE");
    }

    @Test
    void priceUpdateAllowWorksWithoutInheritedWritePermission() {
        assertAllowAddsPermission("PRICE_READ", "PRICE_UPDATE");
    }

    @Test
    void priceReadDoesNotImplyProductRead() {
        User user = userWithRole("CONSULTA", permission("PRICE_READ"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        assertThat(service.resolve(user).effective()).contains("PRICE_READ").doesNotContain("PRODUCT_READ");
    }

    @Test
    void saleCancelDenyDoesNotRemoveSaleUpdate() {
        Permission saleCancel = permission("SALE_CANCEL");
        User user = userWithRole("VENDEDOR", permission("SALE_WRITE"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of(
                override(user, saleCancel, PermissionOverrideEffect.DENY)
        ));

        assertThat(service.resolve(user).effective())
                .contains("SALE_CREATE", "SALE_UPDATE", "SALE_DELETE")
                .doesNotContain("SALE_CANCEL");
    }

    @Test
    void saleUpdateDenyDoesNotRemoveSaleCancel() {
        Permission saleUpdate = permission("SALE_UPDATE");
        User user = userWithRole("VENDEDOR", permission("SALE_WRITE"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of(
                override(user, saleUpdate, PermissionOverrideEffect.DENY)
        ));

        assertThat(service.resolve(user).effective())
                .contains("SALE_CREATE", "SALE_DELETE", "SALE_CANCEL")
                .doesNotContain("SALE_UPDATE");
    }

    @Test
    void saleCancelAllowWorksWithoutInheritedWritePermission() {
        assertAllowAddsPermission("SALE_READ", "SALE_CANCEL");
    }

    @Test
    void saleReadOnlyUserHasNoMutationPermissions() {
        User user = userWithRole("CONSULTA", permission("SALE_READ"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        assertThat(service.resolve(user).effective())
                .containsExactly("SALE_READ")
                .doesNotContain("SALE_CREATE", "SALE_UPDATE", "SALE_DELETE", "SALE_CANCEL");
    }

    @Test
    void expandsLegacyDriverWritePermission() {
        assertLegacyWriteExpansion("DRIVER_WRITE", "DRIVER_CREATE", "DRIVER_UPDATE", "DRIVER_DELETE");
    }

    @Test
    void driverUpdateDenyWinsOverLegacyDriverWrite() {
        assertDenyWinsOverLegacyWrite(
                "DRIVER_WRITE", "DRIVER_UPDATE", "DRIVER_CREATE", "DRIVER_DELETE"
        );
    }

    @Test
    void driverUpdateAllowWorksWithoutInheritedWritePermission() {
        assertAllowAddsPermission("DRIVER_READ", "DRIVER_UPDATE");
    }

    @Test
    void driverReadOnlyUserHasNoMutationPermissions() {
        User user = userWithRole("CONSULTA", permission("DRIVER_READ"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        assertThat(service.resolve(user).effective())
                .containsExactly("DRIVER_READ")
                .doesNotContain("DRIVER_CREATE", "DRIVER_UPDATE", "DRIVER_DELETE");
    }

    @Test
    void routeUpdateDenyRemovesInheritedPermissionOnly() {
        Permission routeUpdate = permission("ROUTE_UPDATE");
        User user = userWithRole(
                "ENCARGADO_RUTA",
                permission("ROUTE_READ"), routeUpdate, permission("ROUTE_CANCEL")
        );
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of(
                override(user, routeUpdate, PermissionOverrideEffect.DENY)
        ));

        assertThat(service.resolve(user).effective())
                .contains("ROUTE_READ", "ROUTE_CANCEL")
                .doesNotContain("ROUTE_UPDATE");
    }

    @Test
    void routeCancelDenyDoesNotRemoveRouteUpdate() {
        Permission routeCancel = permission("ROUTE_CANCEL");
        User user = userWithRole("ENCARGADO_RUTA", permission("ROUTE_UPDATE"), routeCancel);
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of(
                override(user, routeCancel, PermissionOverrideEffect.DENY)
        ));

        assertThat(service.resolve(user).effective())
                .contains("ROUTE_UPDATE")
                .doesNotContain("ROUTE_CANCEL");
    }

    @Test
    void routeUpdateAllowWorksWithoutInheritedPermission() {
        assertAllowAddsPermission("ROUTE_READ", "ROUTE_UPDATE");
    }

    @Test
    void routeCancelAllowWorksWithoutInheritedPermission() {
        assertAllowAddsPermission("ROUTE_READ", "ROUTE_CANCEL");
    }

    @Test
    void routeReadOnlyUserHasNoMutationPermissions() {
        User user = userWithRole("CONSULTA", permission("ROUTE_READ"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        assertThat(service.resolve(user).effective())
                .containsExactly("ROUTE_READ")
                .doesNotContain("ROUTE_CREATE", "ROUTE_UPDATE", "ROUTE_DELETE", "ROUTE_CANCEL");
    }

    @Test
    void expandsLegacyUserWritePermissionIncludingRoleAndPersonalPermissionAssignment() {
        assertLegacyWriteExpansion(
                "USER_WRITE", "USER_CREATE", "USER_UPDATE", "USER_DELETE",
                "USER_ASSIGN_ROLE", "USER_ASSIGN_PERMISSION"
        );
    }

    @Test
    void userAssignPermissionDenyWinsOverLegacyUserWriteWithoutBlockingOtherOperations() {
        assertDenyWinsOverLegacyWrite(
                "USER_WRITE", "USER_ASSIGN_PERMISSION", "USER_CREATE", "USER_UPDATE",
                "USER_DELETE", "USER_ASSIGN_ROLE"
        );
    }

    @Test
    void userAssignPermissionAllowWorksWithoutInheritedWritePermission() {
        assertAllowAddsPermission("USER_READ", "USER_ASSIGN_PERMISSION");
    }

    @Test
    void expandsLegacyRoleWritePermissionAcrossRoleAndPermissionAdministration() {
        assertLegacyWriteExpansion(
                "ROLE_WRITE", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_DELETE", "ROLE_ASSIGN_PERMISSION",
                "PERMISSION_CREATE", "PERMISSION_UPDATE", "PERMISSION_DELETE"
        );
    }

    @Test
    void roleAssignPermissionDenyWinsOverLegacyRoleWriteWithoutBlockingRoleUpdates() {
        assertDenyWinsOverLegacyWrite(
                "ROLE_WRITE", "ROLE_ASSIGN_PERMISSION", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_DELETE",
                "PERMISSION_CREATE", "PERMISSION_UPDATE", "PERMISSION_DELETE"
        );
    }

    @Test
    void allowLegacyWriteExpandsBeforeEffectivePermissionsAreCalculated() {
        Permission legacy = permission("SALE_WRITE");
        User user = userWithRole("CONSULTA", permission("SALE_READ"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of(
                override(user, legacy, PermissionOverrideEffect.ALLOW)
        ));

        EffectivePermissionService.PermissionResolution resolution = service.resolve(user);

        assertThat(resolution.allowed()).contains(
                "SALE_WRITE", "SALE_CREATE", "SALE_UPDATE", "SALE_DELETE", "SALE_CANCEL"
        );
        assertThat(resolution.effective()).containsAll(resolution.allowed());
    }

    @Test
    void denyLegacyWriteRemovesLegacyAndEveryExpandedOperation() {
        Permission legacy = permission("SALE_WRITE");
        User user = userWithRole("VENDEDOR", permission("SALE_WRITE"));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of(
                override(user, legacy, PermissionOverrideEffect.DENY)
        ));

        EffectivePermissionService.PermissionResolution resolution = service.resolve(user);

        assertThat(resolution.denied()).contains(
                "SALE_WRITE", "SALE_CREATE", "SALE_UPDATE", "SALE_DELETE", "SALE_CANCEL"
        );
        assertThat(resolution.effective()).doesNotContain(
                "SALE_WRITE", "SALE_CREATE", "SALE_UPDATE", "SALE_DELETE", "SALE_CANCEL"
        );
    }

    private void assertLegacyWriteExpansion(String legacy, String... granular) {
        User user = userWithRole("LEGACY", permission(legacy));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        assertThat(service.resolve(user).effective()).contains(legacy).contains(granular);
    }

    private void assertDenyWinsOverLegacyWrite(
            String legacy,
            String deniedCode,
            String... remainingGranular
    ) {
        Permission denied = permission(deniedCode);
        User user = userWithRole("LEGACY", permission(legacy));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of(
                override(user, denied, PermissionOverrideEffect.DENY)
        ));

        assertThat(service.resolve(user).effective())
                .contains(remainingGranular)
                .doesNotContain(deniedCode);
    }

    private void assertAllowAddsPermission(String inheritedCode, String allowedCode) {
        Permission allowed = permission(allowedCode);
        User user = userWithRole("CONSULTA", permission(inheritedCode));
        when(overrideRepository.findAllByUserId(user.getId())).thenReturn(List.of(
                override(user, allowed, PermissionOverrideEffect.ALLOW)
        ));

        assertThat(service.resolve(user).effective()).contains(inheritedCode, allowedCode);
    }

    private User userWithRole(String roleName, Permission... permissions) {
        Role role = Role.builder()
                .name(roleName)
                .active(true)
                .permissions(Set.of(permissions))
                .build();
        return User.builder()
                .id(UUID.randomUUID())
                .roles(Set.of(role))
                .build();
    }

    private Permission permission(String code) {
        return Permission.builder().id(UUID.randomUUID()).code(code).module("TEST").build();
    }

    private UserPermissionOverride override(
            User user,
            Permission permission,
            PermissionOverrideEffect effect
    ) {
        return UserPermissionOverride.builder()
                .user(user)
                .permission(permission)
                .effect(effect)
                .build();
    }
}
