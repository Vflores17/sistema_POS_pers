package com.vflores.pos.adminauthorizations.application;

import org.springframework.security.access.AccessDeniedException;

public class AdminAuthorizationRequiredException extends AccessDeniedException {

    public static final String REQUEST_ATTRIBUTE =
            AdminAuthorizationRequiredException.class.getName() + ".required";

    public AdminAuthorizationRequiredException() {
        super("Temporary administrator authorization is required");
    }
}
