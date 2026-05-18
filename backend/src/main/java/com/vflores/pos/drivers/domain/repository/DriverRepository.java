package com.vflores.pos.drivers.domain.repository;

import com.vflores.pos.drivers.domain.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {
}
