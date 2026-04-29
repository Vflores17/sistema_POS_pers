package com.vflores.pos.auth.api.dto;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        String refreshToken
) {
}
