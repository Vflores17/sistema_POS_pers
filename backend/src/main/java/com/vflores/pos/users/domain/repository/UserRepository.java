package com.vflores.pos.users.domain.repository;

import com.vflores.pos.users.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    @Query("""
            SELECT DISTINCT u
            FROM User u
            LEFT JOIN FETCH u.roles r
            LEFT JOIN FETCH r.permissions p
            WHERE lower(u.username) = lower(:identifier)
               OR lower(u.email) = lower(:identifier)
            """)
    Optional<User> findForAuthentication(@Param("identifier") String identifier);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
