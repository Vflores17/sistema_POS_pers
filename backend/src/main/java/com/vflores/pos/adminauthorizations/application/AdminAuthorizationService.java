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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuthorizationService {

    private static final int TOKEN_BYTES = 32;
    private static final long VALIDITY_SECONDS = 90;
    private static final String ADMIN_ROLE = "ADMIN";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Map<String, OperationPolicy> OPERATION_POLICIES = Map.of(
            "SALE_UPDATE", new OperationPolicy("SALE_UPDATE", "SALE"),
            "SALE_CANCEL", new OperationPolicy("SALE_CANCEL", "SALE"),
            "CLIENT_UPDATE", new OperationPolicy("CLIENT_UPDATE", "CLIENT"),
            "PRODUCT_UPDATE", new OperationPolicy("PRODUCT_UPDATE", "PRODUCT"),
            "ROUTE_UPDATE", new OperationPolicy("ROUTE_UPDATE", "ROUTE"),
            "DRIVER_UPDATE", new OperationPolicy("DRIVER_UPDATE", "DRIVER")
    );

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final EffectivePermissionService effectivePermissionService;
    private final AdminAuthorizationRepository authorizationRepository;

    @Transactional
    public AdminAuthorizationResponse issue(UUID requesterId, CreateAdminAuthorizationRequest request) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        OperationPolicy policy = policyFor(request.operationKey());
        String resourceType = normalize(request.resourceType());
        if (!policy.resourceType().equals(resourceType)) {
            throw new AdminAuthorizationRejectedException("Resource type does not match operation");
        }

        User approver = authenticateApprover(request.adminUsername(), request.adminPassword());
        requireAdminApproval(approver, policy.permissionCode());

        String plainToken = generateToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(VALIDITY_SECONDS);
        AdminAuthorization authorization = AdminAuthorization.builder()
                .requester(requester)
                .approver(approver)
                .permissionCode(policy.permissionCode())
                .operationKey(normalize(request.operationKey()))
                .resourceType(resourceType)
                .resourceId(request.resourceId())
                .tokenHash(hash(plainToken))
                .status(AdminAuthorizationStatus.ISSUED)
                .expiresAt(expiresAt)
                .build();

        AdminAuthorization saved = authorizationRepository.save(authorization);
        return new AdminAuthorizationResponse(
                saved.getId(), plainToken, saved.getOperationKey(), saved.getResourceType(),
                saved.getResourceId(), saved.getExpiresAt()
        );
    }

    @Transactional(noRollbackFor = AdminAuthorizationRejectedException.class)
    public AdminAuthorization reserve(
            String token,
            UUID requesterId,
            String operationKey,
            String resourceType,
            UUID resourceId
    ) {
        OperationPolicy policy = policyFor(operationKey);
        String normalizedResourceType = normalize(resourceType);
        if (!policy.resourceType().equals(normalizedResourceType)) {
            throw rejected();
        }

        AdminAuthorization authorization = authorizationRepository.findByTokenHashForUpdate(hash(token))
                .orElseThrow(this::rejected);
        OffsetDateTime now = OffsetDateTime.now();
        if (authorization.getExpiresAt().isBefore(now) || authorization.getExpiresAt().isEqual(now)) {
            if (authorization.getStatus() == AdminAuthorizationStatus.ISSUED) {
                authorization.setStatus(AdminAuthorizationStatus.EXPIRED);
            }
            throw rejected();
        }
        if (authorization.getStatus() != AdminAuthorizationStatus.ISSUED
                || !authorization.getRequester().getId().equals(requesterId)
                || !authorization.getOperationKey().equals(normalize(operationKey))
                || !authorization.getPermissionCode().equals(policy.permissionCode())
                || !authorization.getResourceType().equals(normalizedResourceType)
                || !java.util.Objects.equals(authorization.getResourceId(), resourceId)) {
            throw rejected();
        }

        authorization.setStatus(AdminAuthorizationStatus.RESERVED);
        return authorization;
    }

    @Transactional(noRollbackFor = AdminAuthorizationRejectedException.class)
    public void consume(UUID authorizationId) {
        AdminAuthorization authorization = authorizationRepository.findByIdForUpdate(authorizationId)
                .orElseThrow(this::rejected);
        OffsetDateTime now = OffsetDateTime.now();
        if (authorization.getExpiresAt().isBefore(now) || authorization.getExpiresAt().isEqual(now)) {
            if (authorization.getStatus() == AdminAuthorizationStatus.RESERVED) {
                authorization.setStatus(AdminAuthorizationStatus.EXPIRED);
            }
            throw rejected();
        }
        if (authorization.getStatus() != AdminAuthorizationStatus.RESERVED) {
            throw rejected();
        }
        authorization.setStatus(AdminAuthorizationStatus.CONSUMED);
        authorization.setConsumedAt(now);
    }

    private User authenticateApprover(String username, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
            return userRepository.findById(principal.getId())
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        } catch (AuthenticationException | ClassCastException ex) {
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    private void requireAdminApproval(User approver, String permissionCode) {
        boolean activeAdmin = approver.getStatus() == UserStatus.ACTIVE
                && approver.getRoles().stream().anyMatch(this::isActiveAdminRole);
        if (!activeAdmin) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!effectivePermissionService.resolve(approver).effective().contains(permissionCode)) {
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    private boolean isActiveAdminRole(Role role) {
        return role.isActive() && ADMIN_ROLE.equals(normalize(role.getName()));
    }

    private OperationPolicy policyFor(String operationKey) {
        OperationPolicy policy = OPERATION_POLICIES.get(normalize(operationKey));
        if (policy == null) {
            throw new AdminAuthorizationRejectedException("Operation is not eligible for temporary authorization");
        }
        return policy;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        if (value == null || value.isBlank()) {
            throw rejected();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private AdminAuthorizationRejectedException rejected() {
        return new AdminAuthorizationRejectedException("Temporary authorization is invalid or unavailable");
    }

    private record OperationPolicy(String permissionCode, String resourceType) {
    }
}
