package com.vflores.pos.routesales.domain.repository;

import com.vflores.pos.routesales.domain.model.RouteSaleDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RouteSaleDetailRepository extends JpaRepository<RouteSaleDetail, UUID> {
}
