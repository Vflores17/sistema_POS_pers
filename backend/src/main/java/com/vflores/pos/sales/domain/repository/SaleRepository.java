package com.vflores.pos.sales.domain.repository;

import com.vflores.pos.sales.domain.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    @Query("""
            SELECT DISTINCT s
            FROM Sale s
            LEFT JOIN FETCH s.details d
            LEFT JOIN FETCH d.product p
            ORDER BY s.createdAt DESC
            """)
    List<Sale> findAllWithDetails();

    @Query("""
            SELECT DISTINCT s
            FROM Sale s
            LEFT JOIN FETCH s.details d
            LEFT JOIN FETCH d.product p
            WHERE s.id = :id
            """)
    Optional<Sale> findByIdWithDetails(@Param("id") UUID id);

    @Query("select coalesce(max(s.invoiceNumber), 0) from Sale s")
    Long findMaxInvoiceNumber();
}
