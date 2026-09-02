package com.vflores.pos.adminauthorizations.application;

import com.vflores.pos.adminauthorizations.domain.model.AdminAuthorization;
import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthorizedOperationExecutorTest {

    private static final UUID REQUESTER_ID = UUID.fromString("166df55c-d9a8-41ed-85a9-4bc48c83b04c");
    private static final UUID RESOURCE_ID = UUID.fromString("3ec2b68b-1ce8-4947-a031-299d9c955148");
    private static final UUID AUTHORIZATION_ID = UUID.fromString("16cc1810-711b-4ed1-896b-cb991d075544");

    private AdminAuthorizationService authorizationService;
    private AdminAuthorizedOperationExecutor executor;

    @BeforeEach
    void setUp() {
        authorizationService = mock(AdminAuthorizationService.class);
        executor = new AdminAuthorizedOperationExecutor(authorizationService);
    }

    @Test
    void normalPermissionExecutesWithoutTouchingTemporaryAuthorization() {
        Authentication authentication = authentication("SALE_UPDATE");

        String result = executor.execute(
                authentication, "SALE_UPDATE", "SALE_UPDATE", "SALE", RESOURCE_ID, "unused",
                () -> "updated"
        );

        assertThat(result).isEqualTo("updated");
        verify(authorizationService, never()).reserve(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(authorizationService, never()).consume(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingPermissionAndTokenRequiresAdministratorAuthorization() {
        AtomicBoolean executed = new AtomicBoolean();

        assertThatThrownBy(() -> executor.execute(
                authentication(), "CLIENT_UPDATE", "CLIENT_UPDATE", "CLIENT", RESOURCE_ID, null,
                () -> {
                    executed.set(true);
                    return "updated";
                }
        )).isInstanceOf(AdminAuthorizationRequiredException.class);

        assertThat(executed).isFalse();
    }

    @Test
    void validTokenIsReservedBeforeOperationAndConsumedAfterSuccess() {
        Authentication authentication = authentication();
        AdminAuthorization authorization = AdminAuthorization.builder().id(AUTHORIZATION_ID).build();
        when(authorizationService.reserve(
                "plain-token", REQUESTER_ID, "PRODUCT_UPDATE", "PRODUCT", RESOURCE_ID
        )).thenReturn(authorization);

        String result = executor.execute(
                authentication, "PRODUCT_UPDATE", "PRODUCT_UPDATE", "PRODUCT", RESOURCE_ID,
                "plain-token", () -> "updated"
        );

        assertThat(result).isEqualTo("updated");
        var order = org.mockito.Mockito.inOrder(authorizationService);
        order.verify(authorizationService).reserve(
                "plain-token", REQUESTER_ID, "PRODUCT_UPDATE", "PRODUCT", RESOURCE_ID
        );
        order.verify(authorizationService).consume(AUTHORIZATION_ID);
    }

    @Test
    void rejectedTokenNeverExecutesBusinessOperationOrConsumesAuthorization() {
        Authentication authentication = authentication();
        AtomicBoolean executed = new AtomicBoolean();
        when(authorizationService.reserve(
                "bad-token", REQUESTER_ID, "DRIVER_UPDATE", "DRIVER", RESOURCE_ID
        )).thenThrow(new AdminAuthorizationRejectedException("Temporary authorization is invalid or unavailable"));

        assertThatThrownBy(() -> executor.execute(
                authentication, "DRIVER_UPDATE", "DRIVER_UPDATE", "DRIVER", RESOURCE_ID,
                "bad-token", () -> {
                    executed.set(true);
                    return "updated";
                }
        )).isInstanceOf(AdminAuthorizationRejectedException.class);

        assertThat(executed).isFalse();
        verify(authorizationService, never()).consume(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void businessFailureDoesNotConsumeAuthorization() {
        Authentication authentication = authentication();
        AdminAuthorization authorization = AdminAuthorization.builder().id(AUTHORIZATION_ID).build();
        when(authorizationService.reserve(
                "plain-token", REQUESTER_ID, "ROUTE_UPDATE", "ROUTE", RESOURCE_ID
        )).thenReturn(authorization);

        assertThatThrownBy(() -> executor.execute(
                authentication, "ROUTE_UPDATE", "ROUTE_UPDATE", "ROUTE", RESOURCE_ID,
                "plain-token", () -> {
                    throw new IllegalStateException("business failed");
                }
        )).isInstanceOf(IllegalStateException.class);

        verify(authorizationService, never()).consume(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void executionBoundaryIsTransactional() throws NoSuchMethodException {
        Method method = AdminAuthorizedOperationExecutor.class.getMethod(
                "execute", Authentication.class, String.class, String.class, String.class,
                UUID.class, String.class, java.util.function.Supplier.class
        );

        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void concurrentAttemptsExecuteBusinessOperationOnlyOnce() throws Exception {
        Authentication authentication = authentication();
        AdminAuthorization authorization = AdminAuthorization.builder().id(AUTHORIZATION_ID).build();
        AtomicBoolean reserved = new AtomicBoolean();
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        when(authorizationService.reserve(
                "plain-token", REQUESTER_ID, "SALE_UPDATE", "SALE", RESOURCE_ID
        )).thenAnswer(invocation -> {
            if (!reserved.compareAndSet(false, true)) {
                throw new AdminAuthorizationRejectedException(
                        "Temporary authorization is invalid or unavailable"
                );
            }
            return authorization;
        });

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> executeAfterStart(start, authentication, executions));
            var second = pool.submit(() -> executeAfterStart(start, authentication, executions));
            start.countDown();

            int successes = 0;
            int rejections = 0;
            for (var future : java.util.List.of(first, second)) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                    successes++;
                } catch (java.util.concurrent.ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(AdminAuthorizationRejectedException.class);
                    rejections++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(rejections).isEqualTo(1);
            assertThat(executions).hasValue(1);
            verify(authorizationService).consume(AUTHORIZATION_ID);
        }
    }

    private String executeAfterStart(
            CountDownLatch start,
            Authentication authentication,
            AtomicInteger executions
    ) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        return executor.execute(
                authentication, "SALE_UPDATE", "SALE_UPDATE", "SALE", RESOURCE_ID,
                "plain-token", () -> {
                    executions.incrementAndGet();
                    return "updated";
                }
        );
    }

    private Authentication authentication(String... authorities) {
        Set<SimpleGrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .collect(java.util.stream.Collectors.toSet());
        AuthenticatedUser principal = new AuthenticatedUser(
                REQUESTER_ID, "requester", "", "requester@example.test", "Requester",
                true, false, Set.copyOf(granted)
        );
        return new UsernamePasswordAuthenticationToken(principal, null, granted);
    }
}
