package com.vflores.pos.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        Object meta,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> ok(T data, Object meta) {
        return new ApiResponse<>(true, data, meta, OffsetDateTime.now());
    }
}
