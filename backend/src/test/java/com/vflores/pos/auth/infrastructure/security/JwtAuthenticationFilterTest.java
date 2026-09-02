package com.vflores.pos.auth.infrastructure.security;

import com.vflores.pos.auth.application.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void existingJwtReloadsCurrentAuthoritiesForEveryRequest() throws Exception {
        UserDetails currentUser = mock(UserDetails.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer existing-token");
        when(jwtService.extractUsername("existing-token")).thenReturn("seller");
        when(userDetailsService.loadUserByUsername("seller")).thenReturn(currentUser);
        when(jwtService.isTokenValid("existing-token", currentUser)).thenReturn(true);
        when(currentUser.getAuthorities()).thenAnswer(ignored -> List.of(
                new SimpleGrantedAuthority("SALE_CREATE")
        ));

        filter.doFilterInternal(request, response, filterChain);

        verify(userDetailsService).loadUserByUsername("seller");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("SALE_CREATE");
        verify(filterChain).doFilter(request, response);
    }
}
