package com.vflores.pos.auth.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vflores.pos.adminauthorizations.application.AdminAuthorizationRequiredException;
import com.vflores.pos.shared.exception.ApiErrorResponse;
import com.vflores.pos.shared.logging.DiagnosticLogSupport;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        boolean temporaryRequired = accessDeniedException instanceof AdminAuthorizationRequiredException
                || Boolean.TRUE.equals(request.getAttribute(
                        AdminAuthorizationRequiredException.REQUEST_ATTRIBUTE
                ));
        String code = temporaryRequired ? "ADMIN_AUTHORIZATION_REQUIRED" : "ACCESS_DENIED";
        LOGGER.warn(
                "code={} user={} exceptionType={} technicalMessage=Authorization rejected",
                code,
                DiagnosticLogSupport.currentUser(),
                accessDeniedException.getClass().getName()
        );
        response.getWriter().write(objectMapper.writeValueAsString(ApiErrorResponse.of(
                code,
                temporaryRequired
                        ? "Temporary administrator authorization is required"
                        : "You do not have permission for this action",
                List.of()
        )));
    }
}
