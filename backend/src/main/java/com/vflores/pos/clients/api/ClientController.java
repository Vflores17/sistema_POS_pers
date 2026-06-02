package com.vflores.pos.clients.api;

import com.vflores.pos.clients.api.dto.ClientResponse;
import com.vflores.pos.clients.api.dto.CreateClientRequest;
import com.vflores.pos.clients.api.dto.UpdateClientRequest;
import com.vflores.pos.clients.application.ClientService;
import com.vflores.pos.clients.domain.model.ClientType;
import com.vflores.pos.shared.response.ApiResponse;
import com.vflores.pos.shared.response.PageMeta;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {

    private static final List<String> ALLOWED_SORT_FIELDS = List.of("name", "type", "createdAt", "updatedAt");

    private final ClientService clientService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ClientResponse>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ClientType type,
            @RequestParam(defaultValue = "false") boolean all
    ) {
        // Si all=true, traer todos los registros sin límite de paginación
        Pageable pageable = all
                ? PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.ASC, "name"))
                : toPageable(page, size, sort);

        Page<ClientResponse> clients = clientService.findAll(search, type, pageable);
        PageMeta meta = new PageMeta(
                clients.getNumber(),
                clients.getSize(),
                clients.getTotalElements(),
                clients.getTotalPages(),
                clients.hasNext(),
                clients.hasPrevious()
        );
        return ResponseEntity.ok(ApiResponse.ok(clients.getContent(), meta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(clientService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ClientResponse>> create(@Valid @RequestBody CreateClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(clientService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(clientService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private Pageable toPageable(int page, int size, String sortParam) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);

        String[] sortParts = sortParam.split(",");
        String sortBy = sortParts.length > 0 ? sortParts[0] : "createdAt";
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            sortBy = "createdAt";
        }
        Sort.Direction direction = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(safePage, safeSize, Sort.by(direction, sortBy));
    }
}