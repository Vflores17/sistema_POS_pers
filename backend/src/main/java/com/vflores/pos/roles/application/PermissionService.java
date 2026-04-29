package com.vflores.pos.roles.application;

import com.vflores.pos.roles.api.dto.CreatePermissionRequest;
import com.vflores.pos.roles.api.dto.PermissionResponse;
import com.vflores.pos.roles.api.dto.UpdatePermissionRequest;
import com.vflores.pos.roles.domain.model.Permission;
import com.vflores.pos.roles.domain.repository.PermissionRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public Page<PermissionResponse> findAll(String search, String module, Pageable pageable) {
        Specification<Permission> specification = (root, query, cb) -> cb.conjunction();

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            ));
        }

        if (module != null && !module.isBlank()) {
            String normalized = module.trim().toUpperCase(Locale.ROOT);
            specification = specification.and((root, query, cb) ->
                    cb.equal(cb.upper(root.get("module")), normalized));
        }

        return permissionRepository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PermissionResponse findById(UUID id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));
        return toResponse(permission);
    }

    @Transactional
    public PermissionResponse create(CreatePermissionRequest request) {
        String code = normalizeCode(request.code());
        if (permissionRepository.existsByCode(code)) {
            throw new ConflictException("Permission code already exists");
        }

        Permission permission = Permission.builder()
                .code(code)
                .module(normalizeModule(request.module()))
                .description(normalizeDescription(request.description()))
                .build();
        return toResponse(permissionRepository.save(permission));
    }

    @Transactional
    public PermissionResponse update(UUID id, UpdatePermissionRequest request) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));

        permission.setModule(normalizeModule(request.module()));
        permission.setDescription(normalizeDescription(request.description()));
        return toResponse(permissionRepository.save(permission));
    }

    @Transactional
    public void delete(UUID id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));

        if (!permission.getRoles().isEmpty()) {
            throw new ConflictException("Cannot delete permission assigned to roles");
        }
        permissionRepository.delete(permission);
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeModule(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDescription(String value) {
        return value == null ? null : value.trim();
    }

    private PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getModule(),
                permission.getDescription(),
                permission.getCreatedAt(),
                permission.getUpdatedAt()
        );
    }
}
