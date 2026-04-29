package com.vflores.pos.auth.application;

import com.vflores.pos.auth.api.dto.LoginRequest;
import com.vflores.pos.auth.api.dto.LoginResponse;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDetailsService userDetailsService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (BadCredentialsException ex) {
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
        RefreshToken currentToken = refreshTokenRepository.findByToken(request.refreshToken())
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
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        refreshTokenRepository.revokeAllByUserId(refreshToken.getUser().getId());
    }

    private String createRefreshToken(User user) {
        String value = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshToken token = RefreshToken.builder()
                .token(value)
                .user(user)
                .expiresAt(OffsetDateTime.now().plusSeconds(jwtProperties.refreshTokenExpirationSeconds()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(token);
        return value;
    }
}
