package com.vflores.pos.users.application;

import com.vflores.pos.auth.api.dto.RefreshTokenRequest;
import com.vflores.pos.auth.application.AuthService;
import com.vflores.pos.auth.application.EffectivePermissionService;
import com.vflores.pos.auth.application.JwtService;
import com.vflores.pos.auth.config.JwtProperties;
import com.vflores.pos.auth.domain.model.RefreshToken;
import com.vflores.pos.auth.domain.repository.RefreshTokenRepository;
import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.roles.domain.repository.RoleRepository;
import com.vflores.pos.users.api.dto.UpdateUserRequest;
import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.model.UserStatus;
import com.vflores.pos.users.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceRefreshTokenLifecycleTest {

    private static final UUID ADMIN_ACTOR_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000002");
    private static final UUID ADMIN_ROLE_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000010");
    private static final UUID OTHER_ROLE_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000011");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private EffectivePermissionService effectivePermissionService;

    private AdministrationGuard guard;
    private UserService userService;
    private AuthService authService;
    private Role adminRole;
    private Role otherRole;
    private User adminActor;

    @BeforeEach
    void setUp() {
        guard = new AdministrationGuard(userRepository, roleRepository);
        userService = new UserService(userRepository, roleRepository, passwordEncoder, guard, refreshTokenRepository);
        authService = new AuthService(
                authenticationManager,
                jwtService,
                jwtProperties,
                userRepository,
                refreshTokenRepository,
                userDetailsService,
                effectivePermissionService,
                passwordEncoder);
        adminRole = Role.builder().id(ADMIN_ROLE_ID).name("ADMIN").active(true).build();
        otherRole = Role.builder().id(OTHER_ROLE_ID).name("SELLER").active(true).build();
        adminActor = User.builder().id(ADMIN_ACTOR_ID).username("admin").email("admin@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(adminRole)).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User actor) {
        Set<GrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority("USER_UPDATE"),
                new SimpleGrantedAuthority("USER_ASSIGN_ROLE"));
        var principal = new AuthenticatedUser(actor.getId(), actor.getUsername(), "secret",
                actor.getEmail(), "Actor", true, false, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private UpdateUserRequest updateRequest(UserStatus status, Set<UUID> roleIds) {
        return new UpdateUserRequest("target@example.com", "Target User", status, roleIds);
    }

    private User target(UserStatus status) {
        return User.builder().id(TARGET_ID).username("target").email("target@example.com")
                .passwordHash("hash").status(status).roles(Set.of(otherRole)).build();
    }

    private void stubUniqueGuards() {
        when(userRepository.findByUsernameIgnoreCase(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
    }

    @Test
    void deactivationRevokesRefreshTokens() {
        User target = target(UserStatus.ACTIVE);
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllById(Set.of(OTHER_ROLE_ID))).thenReturn(List.of(otherRole));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubUniqueGuards();
        authenticateAs(adminActor);

        userService.update(TARGET_ID, updateRequest(UserStatus.INACTIVE, Set.of(OTHER_ROLE_ID)));

        verify(refreshTokenRepository).revokeAllByUserId(TARGET_ID);
    }

    @Test
    void blockedUserRefreshTokenIsInvalid() {
        User target = target(UserStatus.ACTIVE);
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllById(Set.of(OTHER_ROLE_ID))).thenReturn(List.of(otherRole));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubUniqueGuards();
        authenticateAs(adminActor);

        userService.update(TARGET_ID, updateRequest(UserStatus.BLOCKED, Set.of(OTHER_ROLE_ID)));

        verify(refreshTokenRepository).revokeAllByUserId(TARGET_ID);
    }

    @Test
    void deactivatedThenReactivatedCannotUseOldRefreshToken() {
        String oldToken = "old-refresh-token";
        User target = User.builder().id(TARGET_ID).username("admin-target").email("target@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(adminRole)).build();
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash(sha256(oldToken))
                .user(target)
                .expiresAt(OffsetDateTime.now().plusMinutes(30))
                .revoked(false)
                .build();

        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllById(Set.of(ADMIN_ROLE_ID))).thenReturn(List.of(adminRole));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ADMIN_ACTOR_ID)).thenReturn(Optional.of(adminActor));
        when(userRepository.countByStatusAndRolesId(UserStatus.ACTIVE, ADMIN_ROLE_ID)).thenReturn(2L);
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByUsernameIgnoreCase(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(refreshTokenRepository.findByTokenHashForUpdate(sha256(oldToken))).thenReturn(Optional.of(stored));
        doAnswer(invocation -> {
            stored.setRevoked(true);
            return null;
        }).when(refreshTokenRepository).revokeAllByUserId(TARGET_ID);
        authenticateAs(adminActor);

        userService.update(TARGET_ID, updateRequest(UserStatus.INACTIVE, Set.of(ADMIN_ROLE_ID)));
        assertThat(stored.isRevoked()).isTrue();

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(oldToken)))
                .isInstanceOf(BadCredentialsException.class);

        userService.update(TARGET_ID, updateRequest(UserStatus.ACTIVE, Set.of(ADMIN_ROLE_ID)));
        assertThat(stored.isRevoked()).isTrue();

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(oldToken)))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenRepository, times(1)).revokeAllByUserId(TARGET_ID);
    }

    @Test
    void deletedUserWithRefreshTokensSucceeds() {
        User target = target(UserStatus.ACTIVE);
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

        assertThatCode(() -> userService.delete(TARGET_ID)).doesNotThrowAnyException();

        verify(refreshTokenRepository).deleteByUserId(TARGET_ID);
        verify(userRepository).delete(target);
    }

    @Test
    void deleteDoesNotLeaveRefreshTokenReferences() {
        User target = target(UserStatus.ACTIVE);
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

        userService.delete(TARGET_ID);

        InOrder inOrder = inOrder(refreshTokenRepository, userRepository);
        inOrder.verify(refreshTokenRepository).deleteByUserId(TARGET_ID);
        inOrder.verify(userRepository).delete(target);
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