package com.vflores.pos.adminauthorizations.application;

import com.vflores.pos.adminauthorizations.domain.model.AdminAuthorizationStatus;
import com.vflores.pos.adminauthorizations.domain.repository.AdminAuthorizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminAuthorizationCleanupServiceTest {

    @Mock
    private AdminAuthorizationRepository repository;

    @Test
    void marksExpiredRecordsAndRemovesOldTerminalRecords() {
        AdminAuthorizationCleanupService service = new AdminAuthorizationCleanupService(repository);

        service.cleanExpiredAuthorizations();

        verify(repository).markExpired(
                any(OffsetDateTime.class),
                eq(java.util.Set.of(AdminAuthorizationStatus.ISSUED, AdminAuthorizationStatus.RESERVED)),
                eq(AdminAuthorizationStatus.EXPIRED)
        );
        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<Collection<AdminAuthorizationStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(repository).deleteExpiredBefore(cutoff.capture(), statuses.capture());
        assertThat(cutoff.getValue()).isBefore(OffsetDateTime.now().minusDays(29));
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                AdminAuthorizationStatus.CONSUMED,
                AdminAuthorizationStatus.EXPIRED,
                AdminAuthorizationStatus.CANCELLED
        );
    }
}
