package com.vflores.pos.shared.config;

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
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/**").hasAuthority("USER_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/**").hasAuthority("USER_WRITE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/**").hasAuthority("USER_WRITE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/**").hasAuthority("USER_WRITE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/**").hasAuthority("USER_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/clients/**").hasAuthority("CLIENT_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/clients/**").hasAuthority("CLIENT_WRITE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/clients/**").hasAuthority("CLIENT_WRITE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/clients/**").hasAuthority("CLIENT_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/*/prices/**").hasAuthority("PRICE_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/*/prices/**").hasAuthority("PRICE_WRITE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/*/prices/**").hasAuthority("PRICE_WRITE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/*/prices/**").hasAuthority("PRICE_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").hasAuthority("PRODUCT_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/**").hasAuthority("PRODUCT_WRITE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/**").hasAuthority("PRODUCT_WRITE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasAuthority("PRODUCT_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/sales/**").hasAuthority("SALE_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/sales/**").hasAuthority("SALE_WRITE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/sales/**").hasAuthority("SALE_WRITE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/sales/**").hasAuthority("SALE_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/roles/**").hasAuthority("ROLE_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/roles/**").hasAuthority("ROLE_WRITE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/roles/**").hasAuthority("ROLE_WRITE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/roles/**").hasAuthority("ROLE_WRITE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/roles/**").hasAuthority("ROLE_WRITE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/permissions/**").hasAuthority("ROLE_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/permissions/**").hasAuthority("ROLE_WRITE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/permissions/**").hasAuthority("ROLE_WRITE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/permissions/**").hasAuthority("ROLE_WRITE")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
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
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
