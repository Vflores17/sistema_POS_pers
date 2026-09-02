package com.vflores.pos.auth.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationAttemptLimiterTest {

    @Test
    void blocksLoginAfterConfiguredFailureLimit() {
        MutableClock clock = new MutableClock();
        AuthenticationAttemptLimiter limiter = new AuthenticationAttemptLimiter(clock);
        String key = limiter.loginKey("127.0.0.1", "seller");

        for (int attempt = 0; attempt < AuthenticationAttemptLimiter.LOGIN_MAX_ATTEMPTS; attempt++) {
            limiter.recordFailure(key, AuthenticationAttemptLimiter.LOGIN_MAX_ATTEMPTS);
        }

        assertThatThrownBy(() -> limiter.checkAllowed(key))
                .isInstanceOf(AuthenticationRateLimitExceededException.class);
    }

    @Test
    void adminAttemptsAreSeparatedByRequesterAndHaveLowerLimit() {
        AuthenticationAttemptLimiter limiter = new AuthenticationAttemptLimiter(new MutableClock());
        String firstRequester = limiter.adminAuthorizationKey("127.0.0.1", "requester-1", "admin");
        String secondRequester = limiter.adminAuthorizationKey("127.0.0.1", "requester-2", "admin");

        for (int attempt = 0; attempt < AuthenticationAttemptLimiter.ADMIN_MAX_ATTEMPTS; attempt++) {
            limiter.recordFailure(firstRequester, AuthenticationAttemptLimiter.ADMIN_MAX_ATTEMPTS);
        }

        assertThatThrownBy(() -> limiter.checkAllowed(firstRequester))
                .isInstanceOf(AuthenticationRateLimitExceededException.class);
        assertThatCode(() -> limiter.checkAllowed(secondRequester)).doesNotThrowAnyException();
    }

    @Test
    void succeedsAgainAfterTemporaryBlockExpires() {
        MutableClock clock = new MutableClock();
        AuthenticationAttemptLimiter limiter = new AuthenticationAttemptLimiter(clock);
        String key = limiter.loginKey("127.0.0.1", "seller");
        for (int attempt = 0; attempt < AuthenticationAttemptLimiter.LOGIN_MAX_ATTEMPTS; attempt++) {
            limiter.recordFailure(key, AuthenticationAttemptLimiter.LOGIN_MAX_ATTEMPTS);
        }

        clock.advance(AuthenticationAttemptLimiter.BLOCK_DURATION.plusSeconds(1));

        assertThatCode(() -> limiter.checkAllowed(key)).doesNotThrowAnyException();
    }

    @Test
    void successfulAuthenticationClearsPreviousFailures() {
        AuthenticationAttemptLimiter limiter = new AuthenticationAttemptLimiter(new MutableClock());
        String key = limiter.loginKey("127.0.0.1", "seller");
        limiter.recordFailure(key, AuthenticationAttemptLimiter.LOGIN_MAX_ATTEMPTS);
        limiter.recordSuccess(key);

        assertThatCode(() -> limiter.checkAllowed(key)).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-09-02T12:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
