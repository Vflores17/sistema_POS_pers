package com.vflores.pos.auth.infrastructure.security;

import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.model.UserStatus;
import com.vflores.pos.users.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final SecurityAuthorityMapper authorityMapper;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = userRepository.findForAuthentication(identifier)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new LockedException("User is blocked");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DisabledException("User is inactive");
        }

        Set<String> activeRoles = authorityMapper.roleNames(user.getRoles());
        if (activeRoles.isEmpty()) {
            throw new DisabledException("User has no active roles");
        }

        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getEmail(),
                user.getFullName(),
                true,
                false,
                authorityMapper.mapAuthorities(user.getRoles())
        );
    }
}
