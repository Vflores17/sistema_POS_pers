package com.vflores.pos.adminauthorizations.application;

import com.vflores.pos.adminauthorizations.domain.model.AdminAuthorization;
import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AdminAuthorizedOperationExecutor {

    private final AdminAuthorizationService adminAuthorizationService;

    @Transactional
    public <T> T execute(
            Authentication authentication,
            String requiredPermission,
            String operationKey,
            String resourceType,
            UUID resourceId,
            String token,
            Supplier<T> operation
    ) {
        if (hasAuthority(authentication, requiredPermission)) {
            return operation.get();
        }
        if (token == null || token.isBlank()) {
            throw new AdminAuthorizationRequiredException();
        }
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser requester)) {
            throw new AccessDeniedException("Temporary authorization cannot be applied");
        }

        AdminAuthorization authorization = adminAuthorizationService.reserve(
                token, requester.getId(), operationKey, resourceType, resourceId
        );
        T result = operation.get();
        adminAuthorizationService.consume(authorization.getId());
        return result;
    }

    private boolean hasAuthority(Authentication authentication, String requiredPermission) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(requiredPermission));
    }
}
