package com.vflores.pos.routesales.domain.repository;

import com.vflores.pos.routesales.domain.model.RouteSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RouteSaleRepository extends JpaRepository<RouteSale, UUID> {

    @Query("""
            SELECT DISTINCT rs
            FROM RouteSale rs
            LEFT JOIN FETCH rs.details d
            LEFT JOIN FETCH d.product p
            ORDER BY rs.createdAt DESC
            """)
    List<RouteSale> findAllWithDetails();

    @Query("""
            SELECT DISTINCT rs
            FROM RouteSale rs
            LEFT JOIN FETCH rs.details d
            LEFT JOIN FETCH d.product p
            WHERE rs.id = :id
            """)
    Optional<RouteSale> findByIdWithDetails(@Param("id") UUID id);

    @Query("select coalesce(max(rs.invoiceNumber), 0) from RouteSale rs")
    Long findMaxInvoiceNumber();
}
