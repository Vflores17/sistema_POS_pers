package com.vflores.pos.shared.application;

import com.vflores.pos.shared.exception.ConflictException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class PaymentValidationSupport {

    private PaymentValidationSupport() {
    }

    public static void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictException("Payment amount must be greater than 0");
        }
    }

    public static void requireValidPeriod(OffsetDateTime from, OffsetDateTime to) {
        if (from.isAfter(to)) {
            throw new ConflictException("Payment period start must not be after its end");
        }
    }
}
