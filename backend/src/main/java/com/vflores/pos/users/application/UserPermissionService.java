package com.vflores.pos.users.application;

import com.vflores.pos.auth.application.EffectivePermissionService;
import com.vflores.pos.auth.application.EffectivePermissionService.PermissionResolution;
import com.vflores.pos.roles.domain.model.Permission;
import com.vflores.pos.roles.domain.repository.PermissionRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.shared.exception.ResourceNotFoundException;
import com.vflores.pos.users.api.dto.PermissionOverrideItemRequest;
import com.vflores.pos.users.api.dto.UserPermissionsResponse;
import com.vflores.pos.users.domain.model.PermissionOverrideEffect;
import com.vflores.pos.users.domain.model.User;
import com.vflores.pos.users.domain.model.UserPermissionOverride;
import com.vflores.pos.users.domain.repository.UserPermissionOverrideRepository;
import com.vflores.pos.users.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.vflores.pos.shared.application.AuthenticatedUserSupport.getCurrentUserId;

@Service
@RequiredArgsConstructor
public class UserPermissionService {

    private static final String OTHER_USER_OVERRIDES_DENIED_MESSAGE =
            "Only an active administrator may manage another user's permission overrides";
    private static final String SELF_ALLOW_DENIED_MESSAGE =
            "A non-administrator may not grant themselves a permission they do not already hold";

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final EffectivePermissionService effectivePermissionService;
    private final AdministrationGuard administrationGuard;

    @Transactional(readOnly = true)
    public UserPermissionsResponse findByUserId(UUID userId) {
        User user = findUser(userId);
        return toResponse(user, effectivePermissionService.resolve(user));
    }

    @Transactional
    public UserPermissionsResponse replace(
            UUID userId,
            List<PermissionOverrideItemRequest> requestedOverrides
    ) {
        User actor = findUser(getCurrentUserId());
        User user = findUser(userId);
        validateNoDuplicatePermissions(requestedOverrides);

        Set<UUID> permissionIds = requestedOverrides.stream()
                .map(PermissionOverrideItemRequest::permissionId)
                .collect(Collectors.toSet());
        Map<UUID, Permission> permissions = new HashMap<>();
        permissionRepository.findAllById(permissionIds)
                .forEach(permission -> permissions.put(permission.getId(), permission));
        if (permissions.size() != permissionIds.size()) {
            throw new ResourceNotFoundException("One or more permission IDs do not exist");
        }

        requireChangeAuthorized(actor, user, requestedOverrides, permissions);

        overrideRepository.deleteAllByUserId(userId);
        overrideRepository.flush();
        List<UserPermissionOverride> replacements = requestedOverrides.stream()
                .map(item -> UserPermissionOverride.builder()
                        .user(user)
                        .permission(permissions.get(item.permissionId()))
                        .effect(item.effect())
                        .createdBy(actor)
                        .build())
                .toList();
        overrideRepository.saveAll(replacements);
        overrideRepository.flush();

        return toResponse(user, effectivePermissionService.resolve(user));
    }

    @Transactional
    public UserPermissionsResponse clear(UUID userId) {
        User actor = findUser(getCurrentUserId());
        User user = findUser(userId);
        if (!administrationGuard.actorIsActiveAdmin() && !user.getId().equals(actor.getId())) {
            throw new AccessDeniedException(OTHER_USER_OVERRIDES_DENIED_MESSAGE);
        }
        overrideRepository.deleteAllByUserId(userId);
        overrideRepository.flush();
        return toResponse(user, effectivePermissionService.resolve(user));
    }

    private void requireChangeAuthorized(
            User actor,
            User user,
            List<PermissionOverrideItemRequest> requestedOverrides,
            Map<UUID, Permission> permissions
    ) {
        if (administrationGuard.actorIsActiveAdmin()) {
            return;
        }
        if (!user.getId().equals(actor.getId())) {
            throw new AccessDeniedException(OTHER_USER_OVERRIDES_DENIED_MESSAGE);
        }
        Set<String> currentEffective = effectivePermissionService.resolve(actor).effective();
        requestedOverrides.stream()
                .filter(item -> item.effect() == PermissionOverrideEffect.ALLOW)
                .map(item -> permissions.get(item.permissionId()))
                .map(Permission::getCode)
                .map(this::normalize)
                .filter(code -> !currentEffective.contains(code))
                .findAny()
                .ifPresent(code -> {
                    throw new AccessDeniedException(SELF_ALLOW_DENIED_MESSAGE);
                });
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private void validateNoDuplicatePermissions(List<PermissionOverrideItemRequest> overrides) {
        Set<UUID> seen = new HashSet<>();
        boolean duplicated = overrides.stream()
                .map(PermissionOverrideItemRequest::permissionId)
                .anyMatch(permissionId -> !seen.add(permissionId));
        if (duplicated) {
            throw new ConflictException("A permission can only have one override per user");
        }
    }

    private UserPermissionsResponse toResponse(User user, PermissionResolution resolution) {
        return new UserPermissionsResponse(
                user.getId(),
                resolution.inherited(),
                resolution.allowed(),
                resolution.denied(),
                resolution.effective()
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
