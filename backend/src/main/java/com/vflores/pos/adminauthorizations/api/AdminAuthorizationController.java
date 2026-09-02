package com.vflores.pos.adminauthorizations.api;

import com.vflores.pos.adminauthorizations.api.dto.AdminAuthorizationResponse;
import com.vflores.pos.adminauthorizations.api.dto.CreateAdminAuthorizationRequest;
import com.vflores.pos.adminauthorizations.application.AdminAuthorizationService;
import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import com.vflores.pos.auth.application.AuthenticationAttemptLimiter;
import com.vflores.pos.shared.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin-authorizations")
@RequiredArgsConstructor
public class AdminAuthorizationController {

    private final AdminAuthorizationService adminAuthorizationService;
    private final AuthenticationAttemptLimiter authenticationAttemptLimiter;

    @PostMapping
    public ResponseEntity<ApiResponse<AdminAuthorizationResponse>> issue(
            @AuthenticationPrincipal AuthenticatedUser requester,
            @Valid @RequestBody CreateAdminAuthorizationRequest request,
            HttpServletRequest httpRequest
    ) {
        String key = authenticationAttemptLimiter.adminAuthorizationKey(
                httpRequest.getRemoteAddr(), requester.getId().toString(), request.adminUsername());
        authenticationAttemptLimiter.checkAllowed(key);
        try {
            AdminAuthorizationResponse response = adminAuthorizationService.issue(requester.getId(), request);
            authenticationAttemptLimiter.recordSuccess(key);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
        } catch (AuthenticationException ex) {
            authenticationAttemptLimiter.recordFailure(key, AuthenticationAttemptLimiter.ADMIN_MAX_ATTEMPTS);
            throw ex;
        }
    }
}
