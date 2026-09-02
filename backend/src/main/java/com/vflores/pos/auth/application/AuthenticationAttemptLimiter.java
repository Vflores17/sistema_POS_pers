package com.vflores.pos.auth.application;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthenticationAttemptLimiter {

    public static final int LOGIN_MAX_ATTEMPTS = 5;
    public static final int ADMIN_MAX_ATTEMPTS = 3;
    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(5);
    static final Duration BLOCK_DURATION = Duration.ofMinutes(5);

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public AuthenticationAttemptLimiter() {
        this(Clock.systemUTC());
    }

    AuthenticationAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public String loginKey(String remoteAddress, String identifier) {
        return key("LOGIN", remoteAddress, null, identifier);
    }

    public String adminAuthorizationKey(String remoteAddress, String requesterId, String adminIdentifier) {
        return key("ADMIN", remoteAddress, requesterId, adminIdentifier);
    }

    public void checkAllowed(String key) {
        Instant now = clock.instant();
        purgeExpired(now);
        attempts.computeIfPresent(key, (ignored, state) -> {
            if (state.blockedUntil != null && now.isBefore(state.blockedUntil)) {
                throw new AuthenticationRateLimitExceededException();
            }
            if (state.blockedUntil != null || !now.isBefore(state.windowStarted.plus(ATTEMPT_WINDOW))) {
                return null;
            }
            return state;
        });
    }

    public void recordFailure(String key, int maxAttempts) {
        Instant now = clock.instant();
        purgeExpired(now);
        attempts.compute(key, (ignored, current) -> {
            AttemptState state = current;
            if (state == null || state.blockedUntil != null
                    || !now.isBefore(state.windowStarted.plus(ATTEMPT_WINDOW))) {
                state = new AttemptState(0, now, null);
            }
            int failures = state.failures + 1;
            Instant blockedUntil = failures >= maxAttempts ? now.plus(BLOCK_DURATION) : null;
            return new AttemptState(failures, state.windowStarted, blockedUntil);
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private String key(String scope, String remoteAddress, String requesterId, String identifier) {
        return String.join("|",
                scope,
                normalize(remoteAddress),
                normalize(requesterId),
                normalize(identifier));
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.substring(0, Math.min(normalized.length(), 160));
    }

    private void purgeExpired(Instant now) {
        attempts.entrySet().removeIf(entry -> {
            AttemptState state = entry.getValue();
            Instant expiresAt = state.blockedUntil != null
                    ? state.blockedUntil
                    : state.windowStarted.plus(ATTEMPT_WINDOW);
            return !now.isBefore(expiresAt);
        });
    }

    private record AttemptState(int failures, Instant windowStarted, Instant blockedUntil) {
    }
}
