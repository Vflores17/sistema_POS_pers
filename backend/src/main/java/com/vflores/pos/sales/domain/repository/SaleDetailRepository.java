package com.vflores.pos.sales.domain.repository;

import com.vflores.pos.sales.domain.model.SaleDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SaleDetailRepository extends JpaRepository<SaleDetail, UUID> {
}
