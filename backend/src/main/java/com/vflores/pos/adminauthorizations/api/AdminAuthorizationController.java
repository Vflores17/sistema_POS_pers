package com.vflores.pos.adminauthorizations.api;

import com.vflores.pos.adminauthorizations.api.dto.AdminAuthorizationResponse;
import com.vflores.pos.adminauthorizations.api.dto.CreateAdminAuthorizationRequest;
import com.vflores.pos.adminauthorizations.application.AdminAuthorizationService;
import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import com.vflores.pos.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin-authorizations")
@RequiredArgsConstructor
public class AdminAuthorizationController {

    private final AdminAuthorizationService adminAuthorizationService;

    @PostMapping
    public ResponseEntity<ApiResponse<AdminAuthorizationResponse>> issue(
            @AuthenticationPrincipal AuthenticatedUser requester,
            @Valid @RequestBody CreateAdminAuthorizationRequest request
    ) {
        AdminAuthorizationResponse response = adminAuthorizationService.issue(requester.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }
}
