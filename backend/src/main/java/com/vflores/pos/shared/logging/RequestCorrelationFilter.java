package com.vflores.pos.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    static final String REQUEST_ID = "requestId";
    static final String HTTP_METHOD = "httpMethod";
    static final String REQUEST_PATH = "requestPath";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        try {
            MDC.put(REQUEST_ID, requestId);
            MDC.put(HTTP_METHOD, request.getMethod());
            MDC.put(REQUEST_PATH, request.getRequestURI());
            response.setHeader("X-Request-Id", requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID);
            MDC.remove(HTTP_METHOD);
            MDC.remove(REQUEST_PATH);
        }
    }
}
