package com.vflores.pos.auth.application;

import com.vflores.pos.auth.api.dto.LoginRequest;
import com.vflores.pos.auth.api.dto.LoginResponse;
import com.vflores.pos.auth.api.dto.CurrentUserResponse;
import com.vflores.pos.auth.api.dto.RefreshTokenRequest;
import com.vflores.pos.auth.config.JwtProperties;
import com.vflores.pos.auth.domain.model.RefreshToken;
import com.vflores.pos.auth.domain.repository.RefreshTokenRepository;
import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDetailsService userDetailsService;
    private final EffectivePermissionService effectivePermissionService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (org.springframework.security.core.AuthenticationException ex) {
            throw new BadCredentialsException("Invalid credentials");
        }

        UserDetails user = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtService.generateAccessToken(user);
        User domainUser = userRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        String refreshToken = createRefreshToken(domainUser);

        return new LoginResponse("Bearer", accessToken, jwtService.getAccessTokenExpirationSeconds(), refreshToken);
    }

    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {
        RefreshToken currentToken = refreshTokenRepository.findByTokenHashForUpdate(hashToken(request.refreshToken()))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (currentToken.isRevoked()) {
            throw new BadCredentialsException("Refresh token revoked");
        }
        if (currentToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadCredentialsException("Refresh token expired");
        }

        User user = currentToken.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());

        currentToken.setRevoked(true);
        String newRefreshToken = createRefreshToken(user);
        String newAccessToken = jwtService.generateAccessToken(userDetails);

        return new LoginResponse("Bearer", newAccessToken, jwtService.getAccessTokenExpirationSeconds(), newRefreshToken);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashForUpdate(hashToken(request.refreshToken()))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        refreshTokenRepository.revokeAllByUserId(refreshToken.getUser().getId());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        Set<String> roles = user.getRoles().stream()
                .filter(role -> role.isActive())
                .map(role -> role.getName().trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roles,
                effectivePermissionService.resolve(user).effective()
        );
    }

    private String createRefreshToken(User user) {
        String value = generateRefreshToken();
        RefreshToken token = RefreshToken.builder()
                .tokenHash(hashToken(value))
                .user(user)
                .expiresAt(OffsetDateTime.now().plusSeconds(jwtProperties.refreshTokenExpirationSeconds()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(token);
        return value;
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
