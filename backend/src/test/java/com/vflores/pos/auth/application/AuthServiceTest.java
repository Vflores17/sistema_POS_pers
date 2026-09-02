package com.vflores.pos.auth.application;

import com.vflores.pos.auth.api.dto.CurrentUserResponse;
import com.vflores.pos.auth.api.dto.LoginRequest;
import com.vflores.pos.auth.api.dto.LoginResponse;
import com.vflores.pos.auth.api.dto.RefreshTokenRequest;
import com.vflores.pos.auth.config.JwtProperties;
import com.vflores.pos.auth.domain.model.RefreshToken;
import com.vflores.pos.auth.domain.repository.RefreshTokenRepository;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.model.UserStatus;
import com.vflores.pos.users.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private EffectivePermissionService effectivePermissionService;

    @InjectMocks
    private AuthService authService;

    @Test
    void meReturnsEffectivePermissions() {
        UUID userId = UUID.randomUUID();
        Role role = Role.builder().name("VENDEDOR").active(true).build();
        User user = User.builder()
                .id(userId)
                .username("seller")
                .email("seller@example.com")
                .fullName("Seller Test")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(role))
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(effectivePermissionService.resolve(user)).thenReturn(
                new EffectivePermissionService.PermissionResolution(
                        Set.of("SALE_WRITE"),
                        Set.of(),
                        Set.of("SALE_UPDATE"),
                        Set.of("SALE_WRITE", "SALE_CREATE")
                )
        );

        CurrentUserResponse response = authService.me(userId);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.roles()).containsExactly("VENDEDOR");
        assertThat(response.permissions()).containsExactlyInAnyOrder("SALE_WRITE", "SALE_CREATE");
    }

    @Test
    void loginPersistsOnlyRefreshTokenHash() throws Exception {
        LoginRequest request = new LoginRequest("seller", "secret123");
        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername("seller").password("hash").authorities("SALE_READ").build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        User user = User.builder().id(UUID.randomUUID()).username("seller").build();

        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any())).thenReturn(authentication);
        when(jwtService.generateAccessToken(principal)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(userRepository.findByUsername("seller")).thenReturn(Optional.of(user));
        when(jwtProperties.refreshTokenExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(request);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(captor.getValue().getTokenHash()).isEqualTo(sha256(response.refreshToken()));
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(response.refreshToken());
    }

    @Test
    void refreshRotatesAValidTokenOnlyOnce() {
        String plainToken = "plain-refresh-token";
        User user = User.builder().id(UUID.randomUUID()).username("seller").build();
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(sha256(plainToken))
                .user(user)
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .revoked(false)
                .build();
        UserDetails details = org.springframework.security.core.userdetails.User
                .withUsername("seller").password("hash").authorities("SALE_READ").build();

        when(refreshTokenRepository.findByTokenHashForUpdate(sha256(plainToken))).thenReturn(Optional.of(stored));
        when(userDetailsService.loadUserByUsername("seller")).thenReturn(details);
        when(jwtService.generateAccessToken(details)).thenReturn("new-access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(jwtProperties.refreshTokenExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.refresh(new RefreshTokenRequest(plainToken));

        assertThat(stored.isRevoked()).isTrue();
        assertThat(response.refreshToken()).isNotEqualTo(plainToken);
        assertThat(response.accessToken()).isEqualTo("new-access-token");
    }

    @Test
    void revokedRefreshTokenCannotBeReused() {
        String plainToken = "already-used-token";
        RefreshToken stored = RefreshToken.builder()
                .tokenHash(sha256(plainToken))
                .revoked(true)
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .build();
        when(refreshTokenRepository.findByTokenHashForUpdate(sha256(plainToken))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(plainToken)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginNormalizesDisabledUserFailureToGenericCredentialsError() {
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DisabledException("User exists but is disabled"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("known-user", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
