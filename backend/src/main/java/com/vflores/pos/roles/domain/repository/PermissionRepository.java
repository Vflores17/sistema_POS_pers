package com.vflores.pos.roles.domain.repository;

import com.vflores.pos.roles.domain.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID>, JpaSpecificationExecutor<Permission> {

    Optional<Permission> findByCode(String code);

    boolean existsByCode(String code);
}
