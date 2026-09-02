package com.vflores.pos.adminauthorizations.application;

import com.vflores.pos.adminauthorizations.domain.model.AdminAuthorizationStatus;
import com.vflores.pos.adminauthorizations.domain.repository.AdminAuthorizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminAuthorizationCleanupService {

    private static final long RETENTION_DAYS = 30;
    private static final Set<AdminAuthorizationStatus> ACTIVE_STATUSES = Set.of(
            AdminAuthorizationStatus.ISSUED,
            AdminAuthorizationStatus.RESERVED
    );
    private static final Set<AdminAuthorizationStatus> TERMINAL_STATUSES = Set.of(
            AdminAuthorizationStatus.CONSUMED,
            AdminAuthorizationStatus.EXPIRED,
            AdminAuthorizationStatus.CANCELLED
    );

    private final AdminAuthorizationRepository authorizationRepository;

    @Scheduled(fixedDelayString = "${app.admin-authorizations.cleanup-interval-ms:300000}")
    @Transactional
    public void cleanExpiredAuthorizations() {
        OffsetDateTime now = OffsetDateTime.now();
        authorizationRepository.markExpired(now, ACTIVE_STATUSES, AdminAuthorizationStatus.EXPIRED);
        authorizationRepository.deleteExpiredBefore(now.minusDays(RETENTION_DAYS), TERMINAL_STATUSES);
    }
}
