package com.vflores.pos.drivers.application;

import com.vflores.pos.drivers.api.dto.CreateDriverRequest;
import com.vflores.pos.drivers.api.dto.DriverResponse;
import com.vflores.pos.drivers.api.dto.UpdateDriverRequest;
import com.vflores.pos.drivers.domain.model.Driver;
import com.vflores.pos.drivers.domain.repository.DriverRepository;
import com.vflores.pos.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;

    @Transactional(readOnly = true)
    public List<DriverResponse> findAll() {
        return driverRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DriverResponse findById(UUID id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        return toResponse(driver);
    }

    @Transactional
    public DriverResponse create(CreateDriverRequest request) {
        Driver driver = Driver.builder()
                .name(request.name().trim())
                .status(request.status())
                .build();
        return toResponse(driverRepository.save(driver));
    }

    @Transactional
    public DriverResponse update(UUID id, UpdateDriverRequest request) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        driver.setName(request.name().trim());
        driver.setStatus(request.status());
        return toResponse(driverRepository.save(driver));
    }

    @Transactional
    public void delete(UUID id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        driverRepository.delete(driver);
    }

    private DriverResponse toResponse(Driver driver) {
        return new DriverResponse(
                driver.getId(),
                driver.getName(),
                driver.getStatus(),
                driver.getCreatedAt()
        );
    }
}
