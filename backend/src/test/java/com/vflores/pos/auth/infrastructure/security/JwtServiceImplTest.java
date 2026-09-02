package com.vflores.pos.auth.infrastructure.security;

import com.vflores.pos.auth.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceImplTest {

    private static final String SECRET = Encoders.BASE64.encode(
            "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
    private JwtServiceImpl service;
    private UserDetails user;

    @BeforeEach
    void setUp() {
        service = new JwtServiceImpl(new JwtProperties(SECRET, "pos-backend", "pos-frontend", 900, 3600));
        user = org.springframework.security.core.userdetails.User
                .withUsername("seller").password("hash").authorities("ROLE_USER").build();
    }

    @Test
    void generatedAccessTokenContainsRequiredContextAndIsValid() {
        String token = service.generateAccessToken(user);

        assertThat(service.isTokenValid(token, user)).isTrue();
    }

    @Test
    void rejectsTokenWithIncorrectIssuerAudienceOrType() {
        assertThatThrownBy(() -> service.isTokenValid(token("other", "pos-frontend", "access"), user))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.isTokenValid(token("pos-backend", "other", "access"), user))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.isTokenValid(token("pos-backend", "pos-frontend", "refresh"), user))
                .isInstanceOf(RuntimeException.class);
    }

    private String token(String issuer, String audience, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject("seller")
                .claim("token_type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(SECRET)))
                .compact();
    }
}
