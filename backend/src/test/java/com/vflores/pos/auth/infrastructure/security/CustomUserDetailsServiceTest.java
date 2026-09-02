package com.vflores.pos.auth.infrastructure.security;

import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.model.UserStatus;
import com.vflores.pos.users.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityAuthorityMapper authorityMapper;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void blockedUserStillCannotAuthenticate() {
        User user = User.builder().status(UserStatus.BLOCKED).roles(Set.of()).build();
        when(userRepository.findForAuthentication("blocked")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.loadUserByUsername("blocked"))
                .isInstanceOf(LockedException.class);
        verifyNoInteractions(authorityMapper);
    }

    @Test
    void inactiveUserStillCannotAuthenticate() {
        User user = User.builder().status(UserStatus.INACTIVE).roles(Set.of()).build();
        when(userRepository.findForAuthentication("inactive")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.loadUserByUsername("inactive"))
                .isInstanceOf(DisabledException.class);
        verifyNoInteractions(authorityMapper);
    }
}
