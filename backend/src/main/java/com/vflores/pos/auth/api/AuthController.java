package com.vflores.pos.auth.api;

import com.vflores.pos.auth.api.dto.LoginRequest;
import com.vflores.pos.auth.api.dto.LoginResponse;
import com.vflores.pos.auth.api.dto.CurrentUserResponse;
import com.vflores.pos.auth.api.dto.RefreshTokenRequest;
import com.vflores.pos.auth.application.AuthService;
import com.vflores.pos.auth.application.AuthenticationAttemptLimiter;
import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import com.vflores.pos.shared.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthenticationAttemptLimiter authenticationAttemptLimiter;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String key = authenticationAttemptLimiter.loginKey(httpRequest.getRemoteAddr(), request.username());
        authenticationAttemptLimiter.checkAllowed(key);
        try {
            LoginResponse response = authService.login(request);
            authenticationAttemptLimiter.recordSuccess(key);
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (AuthenticationException ex) {
            authenticationAttemptLimiter.recordFailure(key, AuthenticationAttemptLimiter.LOGIN_MAX_ATTEMPTS);
            throw ex;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.ok("Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.ok(authService.me(currentUser.getId())));
    }
}
