package com.vflores.pos.clients.application;

import com.vflores.pos.clients.api.dto.ClientResponse;
import com.vflores.pos.clients.api.dto.CreateClientRequest;
import com.vflores.pos.clients.api.dto.UpdateClientRequest;
import com.vflores.pos.clients.domain.model.Client;
import com.vflores.pos.clients.domain.model.ClientType;
import com.vflores.pos.clients.domain.repository.ClientRepository;
import com.vflores.pos.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public Page<ClientResponse> findAll(String search, ClientType type, Pageable pageable) {
        Specification<Client> specification = (root, query, cb) -> cb.conjunction();

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern));
        }

        if (type != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }

        return clientRepository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ClientResponse findById(UUID id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + id));
        return toResponse(client);
    }

    @Transactional
    public ClientResponse create(CreateClientRequest request) {
        Client client = Client.builder()
                .name(request.name().trim())
                .type(request.type())
                .phone(normalizePhone(request.phone()))
                .build();
        return toResponse(clientRepository.save(client));
    }

    @Transactional
    public ClientResponse update(UUID id, UpdateClientRequest request) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + id));

        client.setName(request.name().trim());
        client.setType(request.type());
        client.setPhone(normalizePhone(request.phone()));
        return toResponse(clientRepository.save(client));
    }

    @Transactional
    public void delete(UUID id) {
        if (!clientRepository.existsById(id)) {
            throw new ResourceNotFoundException("Client not found: " + id);
        }
        clientRepository.deleteById(id);
    }

    private String normalizePhone(String value) {
        return value == null ? null : value.trim();
    }

    private ClientResponse toResponse(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getType(),
                client.getPhone(),
                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }
}
