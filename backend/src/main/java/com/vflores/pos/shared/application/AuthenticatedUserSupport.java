package com.vflores.pos.shared.application;

import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class AuthenticatedUserSupport {

    private AuthenticatedUserSupport() {
    }

    public static UUID getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var principal = (AuthenticatedUser) authentication.getPrincipal();
        return principal.getId();
    }
}
