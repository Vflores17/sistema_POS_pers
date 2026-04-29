package com.vflores.pos.shared.exception;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiErrorResponse(
        boolean success,
        ErrorBody error
) {
    public static ApiErrorResponse of(String code, String message, List<FieldErrorItem> fieldErrors) {
        return new ApiErrorResponse(
                false,
                new ErrorBody(code, message, fieldErrors, OffsetDateTime.now())
        );
    }

    public record ErrorBody(
            String code,
            String message,
            List<FieldErrorItem> fieldErrors,
            OffsetDateTime timestamp
    ) {
    }

    public record FieldErrorItem(
            String field,
            String message
    ) {
    }
}
