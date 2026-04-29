package com.vflores.pos.users.application;

import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.roles.domain.repository.RoleRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.shared.exception.ResourceNotFoundException;
import com.vflores.pos.users.api.dto.CreateUserRequest;
import com.vflores.pos.users.api.dto.UpdateUserRequest;
import com.vflores.pos.users.api.dto.UserResponse;
import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.model.UserStatus;
import com.vflores.pos.users.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(String search, UserStatus status, Pageable pageable) {
        Specification<User> specification = (root, query, cb) -> cb.conjunction();

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            Specification<User> searchSpec = (root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("fullName")), pattern)
            );
            specification = specification.and(searchSpec);
        }

        if (status != null) {
            Specification<User> statusSpec = (root, query, cb) -> cb.equal(root.get("status"), status);
            specification = specification.and(statusSpec);
        }

        return userRepository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return toResponse(user);
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        validateUniqueness(request.username(), request.email(), null);
        Set<Role> roles = fetchRolesOrThrow(request.roleIds());

        User user = User.builder()
                .username(request.username().trim())
                .email(request.email().trim().toLowerCase())
                .fullName(request.fullName().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(request.status())
                .roles(roles)
                .build();

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        validateUniqueness(user.getUsername(), request.email(), user.getId());
        Set<Role> roles = fetchRolesOrThrow(request.roleIds());

        user.setEmail(request.email().trim().toLowerCase());
        user.setFullName(request.fullName().trim());
        user.setStatus(request.status());
        user.setRoles(roles);

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
    }

    @Transactional
    public UserResponse assignRoles(UUID userId, Set<UUID> roleIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Set<Role> roles = fetchRolesOrThrow(roleIds);
        user.setRoles(roles);
        return toResponse(userRepository.save(user));
    }

    private Set<Role> fetchRolesOrThrow(Set<UUID> roleIds) {
        Set<Role> roles = new HashSet<>(roleRepository.findAllById(roleIds));
        if (roles.size() != roleIds.size()) {
            throw new ResourceNotFoundException("One or more role IDs do not exist");
        }
        return roles;
    }

    private void validateUniqueness(String username, String email, UUID currentUserId) {
        userRepository.findByUsername(username)
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new ConflictException("Username is already registered");
                });

        userRepository.findByEmail(email.toLowerCase())
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new ConflictException("Email is already registered");
                });
    }

    private UserResponse toResponse(User user) {
        Set<UserResponse.RoleSummary> roleSummaries = user.getRoles().stream()
                .map(role -> new UserResponse.RoleSummary(role.getId(), role.getName()))
                .collect(Collectors.toSet());

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roleSummaries,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
