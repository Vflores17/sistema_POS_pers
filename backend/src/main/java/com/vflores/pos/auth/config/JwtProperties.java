package com.vflores.pos.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        String audience,
        long accessTokenExpirationSeconds,
        long refreshTokenExpirationSeconds
) {
}
