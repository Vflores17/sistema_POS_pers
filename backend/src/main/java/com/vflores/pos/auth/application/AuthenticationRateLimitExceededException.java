package com.vflores.pos.auth.application;

import org.springframework.security.core.AuthenticationException;

public class AuthenticationRateLimitExceededException extends AuthenticationException {
    public AuthenticationRateLimitExceededException() {
        super("Authentication temporarily unavailable");
    }
}
