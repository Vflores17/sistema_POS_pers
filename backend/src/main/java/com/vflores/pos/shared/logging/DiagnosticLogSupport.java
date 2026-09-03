package com.vflores.pos.shared.logging;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;

public final class DiagnosticLogSupport {

    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)(password|secret|authorization|refresh[-_ ]?token|access[-_ ]?token|adminPassword)\\s*[:=]\\s*[^\\s,;]+"
    );

    private DiagnosticLogSupport() {
    }

    public static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? "authenticated" : sanitize(name);
    }

    public static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return sanitize(message == null || message.isBlank() ? "No exception message" : message);
    }

    public static String safeStackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return sanitize(writer.toString());
    }

    private static String sanitize(String value) {
        String sanitized = value.replace("\r", "\\r").replace("\n", "\\n");
        sanitized = BEARER.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        sanitized = JWT.matcher(sanitized).replaceAll("[REDACTED_JWT]");
        return SECRET_VALUE.matcher(sanitized).replaceAll("$1=[REDACTED]");
    }
}
