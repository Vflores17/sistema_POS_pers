package com.vflores.pos.auth.infrastructure.security;

import com.vflores.pos.auth.application.JwtService;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private AuthenticationEntryPoint authenticationEntryPoint;
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
    void activeUserWithValidJwtStillSucceeds() throws Exception {
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

    @Test
    void blockedUserJwtReturns401() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer blocked-token");
        when(jwtService.extractUsername("blocked-token")).thenReturn("blocked");
        when(userDetailsService.loadUserByUsername("blocked"))
                .thenThrow(new LockedException("User is blocked"));

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationEntryPoint).commence(eq(request), eq(response), isA(LockedException.class));
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void inactiveUserJwtReturns401() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer inactive-token");
        when(jwtService.extractUsername("inactive-token")).thenReturn("inactive");
        when(userDetailsService.loadUserByUsername("inactive"))
                .thenThrow(new DisabledException("User is inactive"));

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationEntryPoint).commence(eq(request), eq(response), isA(DisabledException.class));
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void userWithoutActiveRolesJwtReturns401() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer no-role-token");
        when(jwtService.extractUsername("no-role-token")).thenReturn("no-roles");
        when(userDetailsService.loadUserByUsername("no-roles"))
                .thenThrow(new DisabledException("User has no active roles"));

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationEntryPoint).commence(eq(request), eq(response), isA(DisabledException.class));
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void deletedUserJwtReturns401() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer deleted-token");
        when(jwtService.extractUsername("deleted-token")).thenReturn("deleted");
        when(userDetailsService.loadUserByUsername("deleted"))
                .thenThrow(new UsernameNotFoundException("Invalid credentials"));

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationEntryPoint).commence(eq(request), eq(response), isA(UsernameNotFoundException.class));
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void malformedJwtRegressionFallsThroughToAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer malformed-token");
        when(jwtService.extractUsername("malformed-token"))
                .thenThrow(new MalformedJwtException("JWT is malformed"));

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationEntryPoint, never()).commence(any(), any(), any());
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expiredJwtIsNotTrustedAndFallsThroughToAuthentication() throws Exception {
        UserDetails currentUser = mock(UserDetails.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer expired-token");
        when(jwtService.extractUsername("expired-token")).thenReturn("seller");
        when(userDetailsService.loadUserByUsername("seller")).thenReturn(currentUser);
        when(jwtService.isTokenValid("expired-token", currentUser)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(authenticationEntryPoint, never()).commence(any(), any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}