package com.vflores.pos.shared.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerLoggingTest {

    @Test
    void unexpectedExceptionKeepsSafeResponseAndWritesDiagnosticError() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MDC.put("requestId", "logging-test-request");
        MDC.put("httpMethod", "GET");
        MDC.put("requestPath", "/api/v1/logging-test");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("logging-test-user", null, java.util.List.of())
        );

        try {
            ResponseEntity<ApiErrorResponse> response = new GlobalExceptionHandler()
                    .handleGeneric(new IllegalStateException("diagnostic-log-test-marker"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
            assertThat(response.getBody().error().message()).isEqualTo("Unexpected server error");
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "logging-test-request")
                        .containsEntry("httpMethod", "GET")
                        .containsEntry("requestPath", "/api/v1/logging-test");
                assertThat(event.getFormattedMessage())
                        .contains("code=INTERNAL_ERROR")
                        .contains("user=logging-test-user")
                        .contains("exceptionType=java.lang.IllegalStateException")
                        .contains("technicalMessage=diagnostic-log-test-marker")
                        .contains("GlobalExceptionHandlerLoggingTest");
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            SecurityContextHolder.clearContext();
            MDC.clear();
        }
    }
}
