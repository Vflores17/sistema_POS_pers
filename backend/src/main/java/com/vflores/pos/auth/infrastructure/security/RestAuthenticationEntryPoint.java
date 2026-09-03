package com.vflores.pos.auth.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        LOGGER.warn(
                "code=AUTH_UNAUTHORIZED user={} exceptionType={} technicalMessage=Authentication required",
                DiagnosticLogSupport.currentUser(),
                authException.getClass().getName()
        );
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiErrorResponse.of("AUTH_UNAUTHORIZED", "Authentication is required", List.of())
        ));
    }
}
