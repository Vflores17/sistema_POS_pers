package com.vflores.pos.users.application;

import com.vflores.pos.auth.domain.repository.RefreshTokenRepository;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.roles.domain.repository.RoleRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.users.api.dto.CreateUserRequest;
import com.vflores.pos.users.api.dto.UpdateUserRequest;
import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.model.UserStatus;
import com.vflores.pos.users.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceUsernameNormalizationTest {

    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000020");
    private static final UUID ADMIN_ROLE_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000021");
    private static final UUID SELLER_ROLE_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000022");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private AdministrationGuard guard;
    private UserService service;
    private Role adminRole;
    private Role sellerRole;

    @BeforeEach
    void setUp() {
        guard = new AdministrationGuard(userRepository, roleRepository);
        service = new UserService(userRepository, roleRepository, passwordEncoder, guard, refreshTokenRepository);
        adminRole = Role.builder().id(ADMIN_ROLE_ID).name("ADMIN").active(true).build();
        sellerRole = Role.builder().id(SELLER_ROLE_ID).name("SELLER").active(true).build();
    }

    @Test
    void createNormalizesUsernameToLowercase() {
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllById(Set.of(SELLER_ROLE_ID))).thenReturn(List.of(sellerRole));
        when(userRepository.findByUsernameIgnoreCase(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new CreateUserRequest("NewSeller", "seller@example.com", "New Seller",
                "password123", UserStatus.ACTIVE, Set.of(SELLER_ROLE_ID)));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("newseller");
    }

    @Test
    void createRejectsUsernameThatDiffersOnlyByCase() {
        User existing = User.builder().id(UUID.randomUUID()).username("admin").build();
        when(userRepository.findByUsernameIgnoreCase(any())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(new CreateUserRequest("Admin", "other@example.com",
                "Other", "password123", UserStatus.ACTIVE, Set.of(SELLER_ROLE_ID))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateIgnoresSelfWhenComparingUsernameCaseInsensitively() {
        User self = User.builder().id(USER_ID).username("Admin").email("admin@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).roles(Set.of(sellerRole)).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(self));
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(self));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(self));
        when(roleRepository.findByNameForUpdate("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllById(Set.of(SELLER_ROLE_ID))).thenReturn(List.of(sellerRole));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> service.update(USER_ID,
                new UpdateUserRequest("admin@example.com", "Admin User", UserStatus.ACTIVE, Set.of(SELLER_ROLE_ID))))
                .doesNotThrowAnyException();
    }
}