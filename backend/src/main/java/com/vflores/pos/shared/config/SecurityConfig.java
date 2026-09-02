package com.vflores.pos.shared.config;

import com.vflores.pos.adminauthorizations.application.AdminAuthorizationRequiredException;
import com.vflores.pos.auth.infrastructure.security.CustomUserDetailsService;
import com.vflores.pos.auth.infrastructure.security.JwtAuthenticationFilter;
import com.vflores.pos.auth.infrastructure.security.RestAccessDeniedHandler;
import com.vflores.pos.auth.infrastructure.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public AuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authenticationProvider,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin-authorizations").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/**").hasAuthority("USER_READ")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/*/permission-overrides")
                        .hasAuthority("USER_ASSIGN_PERMISSION")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/*/permission-overrides")
                        .hasAuthority("USER_ASSIGN_PERMISSION")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/*/roles").hasAuthority("USER_ASSIGN_ROLE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").access(requireAll("USER_CREATE", "USER_ASSIGN_ROLE"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/*").access(requireAll("USER_UPDATE", "USER_ASSIGN_ROLE"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/*").hasAuthority("USER_DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/clients/**").hasAuthority("CLIENT_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/clients/**").hasAuthority("CLIENT_CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/clients/*")
                        .access(temporaryOrAuthority("CLIENT_UPDATE"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/clients/**").hasAuthority("CLIENT_DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/prices/all").hasAuthority("PRICE_READ")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/*/prices/**").hasAuthority("PRICE_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/*/prices/**").hasAuthority("PRICE_CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/*/prices/**").hasAuthority("PRICE_UPDATE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/*/prices/**").hasAuthority("PRICE_DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").hasAuthority("PRODUCT_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/**").hasAuthority("PRODUCT_CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/*")
                        .access(temporaryOrAuthority("PRODUCT_UPDATE"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/**").hasAuthority("PRODUCT_UPDATE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasAuthority("PRODUCT_DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/sales/next-invoice-number").hasAuthority("SALE_CREATE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/sales/*/payments").hasAuthority("SALE_UPDATE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/sales/*/status").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/sales/**").hasAuthority("SALE_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/sales/**").hasAuthority("SALE_CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/sales/*")
                        .access(temporaryOrAuthority("SALE_UPDATE"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/sales/**").hasAuthority("SALE_DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/drivers/**").hasAuthority("DRIVER_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/drivers/**").hasAuthority("DRIVER_CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/drivers/*")
                        .access(temporaryOrAuthority("DRIVER_UPDATE"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/drivers/**").hasAuthority("DRIVER_DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/route-sales/next-invoice-number").hasAuthority("ROUTE_CREATE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/route-sales/*/payments").hasAuthority("ROUTE_UPDATE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/route-sales/*/status").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/route-sales/**").hasAuthority("ROUTE_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/route-sales/**").hasAuthority("ROUTE_CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/route-sales/*")
                        .access(temporaryOrAuthority("ROUTE_UPDATE"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/route-sales/**").hasAuthority("ROUTE_DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/roles/**").hasAuthority("ROLE_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/roles/**").hasAuthority("ROLE_CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/roles/**").hasAuthority("ROLE_UPDATE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/roles/*/permissions")
                        .hasAuthority("ROLE_ASSIGN_PERMISSION")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/roles/**").hasAuthority("ROLE_DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/permissions/**").hasAuthority("PERMISSION_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/permissions/**").hasAuthority("PERMISSION_CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/permissions/**").hasAuthority("PERMISSION_UPDATE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/permissions/**").hasAuthority("PERMISSION_DELETE")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @SafeVarargs
    private static AuthorizationManager<RequestAuthorizationContext> requireAll(String... authorities) {
        AuthorizationManager<RequestAuthorizationContext>[] managers = java.util.Arrays.stream(authorities)
                .map(AuthorityAuthorizationManager::<RequestAuthorizationContext>hasAuthority)
                .toArray(AuthorizationManager[]::new);
        return AuthorizationManagers.allOf(managers);
    }

    private static AuthorizationManager<RequestAuthorizationContext> temporaryOrAuthority(String authority) {
        return (authenticationSupplier, context) -> {
            var authentication = authenticationSupplier.get();
            if (authentication != null && authentication.isAuthenticated()) {
                boolean hasAuthority = authentication.getAuthorities().stream()
                        .anyMatch(granted -> granted.getAuthority().equals(authority));
                if (hasAuthority) {
                    return new AuthorizationDecision(true);
                }
                String token = context.getRequest().getHeader("X-Admin-Authorization");
                if (token != null && !token.isBlank()) {
                    return new AuthorizationDecision(true);
                }
                throw new AdminAuthorizationRequiredException();
            }
            return new AuthorizationDecision(false);
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Admin-Authorization"
        ));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
