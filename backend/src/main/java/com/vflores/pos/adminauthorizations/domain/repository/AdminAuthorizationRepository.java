package com.vflores.pos.adminauthorizations.domain.repository;

import com.vflores.pos.adminauthorizations.domain.model.AdminAuthorization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import com.vflores.pos.adminauthorizations.domain.model.AdminAuthorizationStatus;
import org.springframework.data.jpa.repository.Modifying;

public interface AdminAuthorizationRepository extends JpaRepository<AdminAuthorization, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT authorization FROM AdminAuthorization authorization WHERE authorization.tokenHash = :tokenHash")
    Optional<AdminAuthorization> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT authorization FROM AdminAuthorization authorization WHERE authorization.id = :id")
    Optional<AdminAuthorization> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("""
            update AdminAuthorization authorization
            set authorization.status = :expiredStatus
            where authorization.status in :activeStatuses
              and authorization.expiresAt <= :now
            """)
    int markExpired(
            @Param("now") OffsetDateTime now,
            @Param("activeStatuses") java.util.Collection<AdminAuthorizationStatus> activeStatuses,
            @Param("expiredStatus") AdminAuthorizationStatus expiredStatus
    );

    @Modifying
    @Query("""
            delete from AdminAuthorization authorization
            where authorization.status in :terminalStatuses
              and authorization.expiresAt < :cutoff
            """)
    int deleteExpiredBefore(
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("terminalStatuses") java.util.Collection<AdminAuthorizationStatus> terminalStatuses
    );
}
