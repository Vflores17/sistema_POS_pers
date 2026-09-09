package com.vflores.pos.auth.infrastructure.security;

import com.vflores.pos.roles.domain.model.Role;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(userRepository.findAllForAuthentication("blocked")).thenReturn(List.of(user));

        assertThatThrownBy(() -> service.loadUserByUsername("blocked"))
                .isInstanceOf(LockedException.class);
        verifyNoInteractions(authorityMapper);
    }

    @Test
    void inactiveUserStillCannotAuthenticate() {
        User user = User.builder().status(UserStatus.INACTIVE).roles(Set.of()).build();
        when(userRepository.findAllForAuthentication("inactive")).thenReturn(List.of(user));

        assertThatThrownBy(() -> service.loadUserByUsername("inactive"))
                .isInstanceOf(DisabledException.class);
        verifyNoInteractions(authorityMapper);
    }

    @Test
    void ambiguousCaseVariantsResolveWithout500() {
        Role role = Role.builder().name("ADMIN").active(true).build();
        User older = User.builder().username("admin").status(UserStatus.ACTIVE).roles(Set.of(role)).build();
        User duplicate = User.builder().username("Admin").roles(Set.of()).build();
        when(userRepository.findAllForAuthentication("admin")).thenReturn(List.of(older, duplicate));
        when(authorityMapper.roleNames(Set.of(role))).thenReturn(Set.of("ADMIN"));
        when(authorityMapper.mapAuthorities(older))
                .thenReturn(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        UserDetails resolved = service.loadUserByUsername("admin");

        assertThat(resolved.getUsername()).isEqualTo("admin");
    }
}
