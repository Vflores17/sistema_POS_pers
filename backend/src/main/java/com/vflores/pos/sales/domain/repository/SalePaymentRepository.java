package com.vflores.pos.sales.domain.repository;

import com.vflores.pos.sales.domain.model.SalePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface SalePaymentRepository extends JpaRepository<SalePayment, UUID> {

    List<SalePayment> findBySaleId(UUID saleId);

    void deleteBySaleId(UUID saleId);

    List<SalePayment> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
            OffsetDateTime from, OffsetDateTime to);
}
