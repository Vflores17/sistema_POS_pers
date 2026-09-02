package com.vflores.pos.adminauthorizations.domain.repository;

import com.vflores.pos.adminauthorizations.domain.model.AdminAuthorization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AdminAuthorizationRepository extends JpaRepository<AdminAuthorization, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT authorization FROM AdminAuthorization authorization WHERE authorization.tokenHash = :tokenHash")
    Optional<AdminAuthorization> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT authorization FROM AdminAuthorization authorization WHERE authorization.id = :id")
    Optional<AdminAuthorization> findByIdForUpdate(@Param("id") UUID id);
}
