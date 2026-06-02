package com.vflores.pos.routesales.domain.model;

import com.vflores.pos.sales.domain.model.Sale;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "route_sales")
public class RouteSale {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "invoice_number", nullable = false, unique = true)
    private Long invoiceNumber;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    @Builder.Default
    private Sale.PaymentMethod paymentMethod = Sale.PaymentMethod.CASH;

    @Column(name = "total", nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RouteStatus status = RouteStatus.PENDING;

    @Column(name = "comments")
    private String comments;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "routeSale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RouteSaleDetail> details = new ArrayList<>();

    @OneToMany(mappedBy = "routeSale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RouteSalePayment> payments = new ArrayList<>();

    public enum RouteStatus {
        PENDING,
        PARTIAL,
        PAID,
        CANCELLED
    }
}
