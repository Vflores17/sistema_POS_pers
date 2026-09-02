package com.vflores.pos.adminauthorizations.application;

import com.vflores.pos.adminauthorizations.api.dto.AdminAuthorizationResponse;
import com.vflores.pos.adminauthorizations.api.dto.CreateAdminAuthorizationRequest;
import com.vflores.pos.adminauthorizations.domain.model.AdminAuthorization;
import com.vflores.pos.adminauthorizations.domain.model.AdminAuthorizationStatus;
import com.vflores.pos.adminauthorizations.domain.repository.AdminAuthorizationRepository;
import com.vflores.pos.auth.application.EffectivePermissionService;
import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.model.UserStatus;
import com.vflores.pos.users.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthorizationServiceTest {

    private static final UUID REQUESTER_ID = UUID.fromString("3837f32e-fd1c-473f-9064-6f7364372a11");
    private static final UUID ADMIN_ID = UUID.fromString("72440ee9-ed63-4301-999c-2f9c0c8384ba");
    private static final UUID RESOURCE_ID = UUID.fromString("87f68128-37f8-4974-8dc3-f753d84f2ec3");

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EffectivePermissionService effectivePermissionService;
    @Mock
    private AdminAuthorizationRepository authorizationRepository;

    private AdminAuthorizationService service;
    private User requester;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AdminAuthorizationService(
                authenticationManager, userRepository, effectivePermissionService, authorizationRepository
        );
        requester = user(REQUESTER_ID, "cashier", UserStatus.ACTIVE, "CASHIER");
        admin = user(ADMIN_ID, "admin", UserStatus.ACTIVE, "ADMIN");
    }

    @Test
    void validAdminIssuesAuthorizationAndPersistsOnlyTokenHash() {
        prepareValidIssue("SALE_UPDATE");
        when(authorizationRepository.save(any())).thenAnswer(invocation -> {
            AdminAuthorization authorization = invocation.getArgument(0);
            authorization.setId(UUID.randomUUID());
            return authorization;
        });

        AdminAuthorizationResponse response = service.issue(REQUESTER_ID, request("SALE_UPDATE", "SALE"));

        ArgumentCaptor<AdminAuthorization> captor = ArgumentCaptor.forClass(AdminAuthorization.class);
        verify(authorizationRepository).save(captor.capture());
        AdminAuthorization persisted = captor.getValue();
        assertThat(response.token()).isNotBlank();
        assertThat(persisted.getTokenHash()).isEqualTo(hash(response.token()));
        assertThat(persisted.getTokenHash()).doesNotContain(response.token());
        assertThat(persisted.getStatus()).isEqualTo(AdminAuthorizationStatus.ISSUED);
        assertThat(persisted.getRequester()).isSameAs(requester);
        assertThat(persisted.getApprover()).isSameAs(admin);
        assertThat(persisted.getPermissionCode()).isEqualTo("SALE_UPDATE");
        assertThat(persisted.getExpiresAt()).isAfter(OffsetDateTime.now().plusSeconds(80));
        assertThat(AdminAuthorization.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("adminPassword", "token");
    }

    @Test
    void incorrectPasswordIsRejectedGenerically() {
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(requester));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("database detail"));

        assertThatThrownBy(() -> service.issue(REQUESTER_ID, request("SALE_UPDATE", "SALE")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
        verify(authorizationRepository, never()).save(any());
    }

    @Test
    void inactiveAdminIsRejectedGenerically() {
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(requester));
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("inactive"));

        assertThatThrownBy(() -> service.issue(REQUESTER_ID, request("SALE_UPDATE", "SALE")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void authenticatedNonAdminIsRejected() {
        User nonAdmin = user(ADMIN_ID, "seller", UserStatus.ACTIVE, "VENDEDOR");
        prepareAuthenticatedApprover(nonAdmin);

        assertThatThrownBy(() -> service.issue(REQUESTER_ID, request("SALE_UPDATE", "SALE")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void operationOutsideAllowlistIsRejectedBeforeAdminAuthentication() {
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(requester));

        assertThatThrownBy(() -> service.issue(REQUESTER_ID, request("SALE_DELETE", "SALE")))
                .isInstanceOf(AdminAuthorizationRejectedException.class);
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void adminWithoutExactPermissionIsRejected() {
        prepareAuthenticatedApprover(admin);
        when(effectivePermissionService.resolve(admin)).thenReturn(resolution(Set.of("SALE_READ")));

        assertThatThrownBy(() -> service.issue(REQUESTER_ID, request("SALE_UPDATE", "SALE")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void validTokenCanBeReservedAndThenCannotBeReused() {
        String token = "one-time-token";
        AdminAuthorization authorization = issued(token, REQUESTER_ID, "SALE_UPDATE", "SALE", RESOURCE_ID);
        when(authorizationRepository.findByTokenHashForUpdate(hash(token))).thenReturn(Optional.of(authorization));

        AdminAuthorization reserved = service.reserve(token, REQUESTER_ID, "SALE_UPDATE", "SALE", RESOURCE_ID);

        assertThat(reserved.getStatus()).isEqualTo(AdminAuthorizationStatus.RESERVED);
        assertThatThrownBy(() -> service.reserve(token, REQUESTER_ID, "SALE_UPDATE", "SALE", RESOURCE_ID))
                .isInstanceOf(AdminAuthorizationRejectedException.class);
    }

    @Test
    void expiredTokenIsMarkedExpiredAndRejected() {
        String token = "expired-token";
        AdminAuthorization authorization = issued(token, REQUESTER_ID, "SALE_UPDATE", "SALE", RESOURCE_ID);
        authorization.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        when(authorizationRepository.findByTokenHashForUpdate(hash(token))).thenReturn(Optional.of(authorization));

        assertThatThrownBy(() -> service.reserve(token, REQUESTER_ID, "SALE_UPDATE", "SALE", RESOURCE_ID))
                .isInstanceOf(AdminAuthorizationRejectedException.class);
        assertThat(authorization.getStatus()).isEqualTo(AdminAuthorizationStatus.EXPIRED);
    }

    @Test
    void tokenMustMatchRequesterOperationAndResource() {
        assertMismatch(UUID.randomUUID(), "SALE_UPDATE", "SALE", RESOURCE_ID);
        assertMismatch(REQUESTER_ID, "SALE_CANCEL", "SALE", RESOURCE_ID);
        assertMismatch(REQUESTER_ID, "SALE_UPDATE", "SALE", UUID.randomUUID());
        assertMismatch(REQUESTER_ID, "SALE_UPDATE", "CLIENT", RESOURCE_ID);
    }

    @Test
    void onlyReservedAuthorizationCanBeConsumedOnce() {
        AdminAuthorization authorization = issued("consume-token", REQUESTER_ID, "SALE_UPDATE", "SALE", RESOURCE_ID);
        authorization.setStatus(AdminAuthorizationStatus.RESERVED);
        when(authorizationRepository.findByIdForUpdate(authorization.getId())).thenReturn(Optional.of(authorization));

        service.consume(authorization.getId());

        assertThat(authorization.getStatus()).isEqualTo(AdminAuthorizationStatus.CONSUMED);
        assertThat(authorization.getConsumedAt()).isNotNull();
        assertThatThrownBy(() -> service.consume(authorization.getId()))
                .isInstanceOf(AdminAuthorizationRejectedException.class);
    }

    @Test
    void expiredReservationCannotBeConsumed() {
        AdminAuthorization authorization = issued("reserved-expired", REQUESTER_ID, "SALE_UPDATE", "SALE", RESOURCE_ID);
        authorization.setStatus(AdminAuthorizationStatus.RESERVED);
        authorization.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        when(authorizationRepository.findByIdForUpdate(authorization.getId())).thenReturn(Optional.of(authorization));

        assertThatThrownBy(() -> service.consume(authorization.getId()))
                .isInstanceOf(AdminAuthorizationRejectedException.class);
        assertThat(authorization.getStatus()).isEqualTo(AdminAuthorizationStatus.EXPIRED);
        assertThat(authorization.getConsumedAt()).isNull();
    }

    private void assertMismatch(UUID requesterId, String operation, String resourceType, UUID resourceId) {
        String token = UUID.randomUUID().toString();
        AdminAuthorization authorization = issued(token, REQUESTER_ID, "SALE_UPDATE", "SALE", RESOURCE_ID);
        if ("SALE".equals(resourceType)) {
            when(authorizationRepository.findByTokenHashForUpdate(hash(token))).thenReturn(Optional.of(authorization));
        }
        assertThatThrownBy(() -> service.reserve(token, requesterId, operation, resourceType, resourceId))
                .isInstanceOf(AdminAuthorizationRejectedException.class);
    }

    private void prepareValidIssue(String permission) {
        prepareAuthenticatedApprover(admin);
        when(effectivePermissionService.resolve(admin)).thenReturn(resolution(Set.of(permission)));
    }

    private void prepareAuthenticatedApprover(User approver) {
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(requester));
        AuthenticatedUser principal = new AuthenticatedUser(
                approver.getId(), approver.getUsername(), "hash", "admin@example.com", "Admin",
                true, false, Set.of()
        );
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken(principal, null, Set.of()));
        when(userRepository.findById(approver.getId())).thenReturn(Optional.of(approver));
    }

    private EffectivePermissionService.PermissionResolution resolution(Set<String> effective) {
        return new EffectivePermissionService.PermissionResolution(Set.of(), Set.of(), Set.of(), effective);
    }

    private CreateAdminAuthorizationRequest request(String operation, String resourceType) {
        return new CreateAdminAuthorizationRequest("admin", "secret-password", operation, resourceType, RESOURCE_ID);
    }

    private User user(UUID id, String username, UserStatus status, String roleName) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .fullName(username)
                .passwordHash("bcrypt-hash")
                .status(status)
                .roles(Set.of(Role.builder().name(roleName).active(true).build()))
                .build();
    }

    private AdminAuthorization issued(
            String token,
            UUID requesterId,
            String operation,
            String resourceType,
            UUID resourceId
    ) {
        return AdminAuthorization.builder()
                .id(UUID.randomUUID())
                .requester(user(requesterId, "requester", UserStatus.ACTIVE, "CASHIER"))
                .approver(admin)
                .permissionCode(operation)
                .operationKey(operation)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .tokenHash(hash(token))
                .status(AdminAuthorizationStatus.ISSUED)
                .expiresAt(OffsetDateTime.now().plusMinutes(1))
                .build();
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
