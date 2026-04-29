package com.vflores.pos.roles.application;

import com.vflores.pos.roles.api.dto.CreateRoleRequest;
import com.vflores.pos.roles.api.dto.RoleResponse;
import com.vflores.pos.roles.api.dto.UpdateRoleRequest;
import com.vflores.pos.roles.domain.model.Permission;
import com.vflores.pos.roles.domain.model.Role;
import com.vflores.pos.roles.domain.repository.PermissionRepository;
import com.vflores.pos.roles.domain.repository.RoleRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public Page<RoleResponse> findAll(String search, Boolean active, Pageable pageable) {
        Specification<Role> specification = (root, query, cb) -> cb.conjunction();

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            ));
        }

        if (active != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }

        return roleRepository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RoleResponse findById(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
        return toResponse(role);
    }

    @Transactional
    public RoleResponse create(CreateRoleRequest request) {
        String normalizedName = normalizeRoleName(request.name());
        if (roleRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new ConflictException("Role name already exists");
        }

        Role role = Role.builder()
                .name(normalizedName)
                .description(normalizeDescription(request.description()))
                .active(request.active())
                .build();
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse update(UUID id, UpdateRoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));

        role.setDescription(normalizeDescription(request.description()));
        role.setActive(request.active());
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    public void delete(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));

        if (!role.getUsers().isEmpty()) {
            throw new ConflictException("Cannot delete role with assigned users");
        }
        roleRepository.delete(role);
    }

    @Transactional
    public RoleResponse assignPermissions(UUID roleId, Set<UUID> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));

        Set<Permission> permissions = permissionRepository.findAllById(permissionIds).stream()
                .collect(Collectors.toSet());

        if (permissions.size() != permissionIds.size()) {
            throw new ResourceNotFoundException("One or more permission IDs do not exist");
        }

        role.setPermissions(permissions);
        return toResponse(roleRepository.save(role));
    }

    private String normalizeRoleName(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDescription(String value) {
        return value == null ? null : value.trim();
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.isActive(),
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }
}
