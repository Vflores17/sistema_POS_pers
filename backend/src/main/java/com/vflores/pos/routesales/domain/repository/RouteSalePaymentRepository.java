package com.vflores.pos.routesales.domain.repository;

import com.vflores.pos.routesales.domain.model.RouteSalePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RouteSalePaymentRepository extends JpaRepository<RouteSalePayment, UUID> {

    List<RouteSalePayment> findByRouteSaleId(UUID routeSaleId);

    void deleteByRouteSaleId(UUID routeSaleId);
}
