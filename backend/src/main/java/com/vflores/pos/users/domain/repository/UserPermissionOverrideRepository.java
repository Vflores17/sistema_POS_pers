package com.vflores.pos.users.domain.repository;

import com.vflores.pos.users.domain.model.UserPermissionOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, UUID> {

    List<UserPermissionOverride> findAllByUserId(UUID userId);

    void deleteAllByUserId(UUID userId);
}
