package com.vflores.pos.auth.application;

import com.vflores.pos.auth.api.dto.CurrentUserResponse;
import com.vflores.pos.auth.config.JwtProperties;
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
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
}
