package com.vflores.pos.routesales.application;

import com.vflores.pos.clients.domain.repository.ClientRepository;
import com.vflores.pos.drivers.domain.repository.DriverRepository;
import com.vflores.pos.products.domain.repository.ProductPriceRepository;
import com.vflores.pos.products.domain.repository.ProductRepository;
import com.vflores.pos.routesales.api.dto.CreateRouteSalePaymentRequest;
import com.vflores.pos.routesales.domain.model.RouteSale;
import com.vflores.pos.routesales.domain.model.RouteSalePayment;
import com.vflores.pos.routesales.domain.repository.RouteSalePaymentRepository;
import com.vflores.pos.routesales.domain.repository.RouteSaleRepository;
import com.vflores.pos.sales.domain.model.Sale;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteSaleServicePaymentHistoryTest {

    @Test
    void returnsOnlyPaymentMovementsProvidedByTheRequestedPeriod() {
        RouteSaleRepository routeSaleRepository = mock(RouteSaleRepository.class);
        RouteSalePaymentRepository paymentRepository = mock(RouteSalePaymentRepository.class);
        RouteSaleService service = new RouteSaleService(routeSaleRepository, paymentRepository,
                mock(ProductRepository.class), mock(ClientRepository.class), mock(DriverRepository.class),
                mock(ProductPriceRepository.class));
        OffsetDateTime from = OffsetDateTime.now().minusHours(6);
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime routeCreatedAt = from.minusDays(4);
        RouteSale routeSale = RouteSale.builder().id(UUID.randomUUID()).invoiceNumber(8L)
                .clientId(UUID.randomUUID()).createdAt(routeCreatedAt).build();
        RouteSalePayment payment = RouteSalePayment.builder().id(UUID.randomUUID()).routeSale(routeSale)
                .method(Sale.PaymentMethod.SINPE).amount(new BigDecimal("30.00"))
                .createdAt(from.plusMinutes(10)).build();
        when(paymentRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAsc(from, to))
                .thenReturn(List.of(payment));

        var movements = service.findPaymentMovements(from, to);

        assertThat(movements).singleElement().satisfies(movement -> {
            assertThat(movement.routeSaleId()).isEqualTo(routeSale.getId());
            assertThat(movement.invoiceNumber()).isEqualTo(8L);
            assertThat(movement.routeSaleCreatedAt()).isEqualTo(routeCreatedAt);
            assertThat(movement.createdAt()).isEqualTo(payment.getCreatedAt());
        });
    }

    @Test
    void appendsNewPaymentWithoutReplacingHistoricalPayment() {
        RouteSaleRepository routeSaleRepository = mock(RouteSaleRepository.class);
        RouteSaleService service = new RouteSaleService(routeSaleRepository,
                mock(RouteSalePaymentRepository.class), mock(ProductRepository.class),
                mock(ClientRepository.class), mock(DriverRepository.class),
                mock(ProductPriceRepository.class));

        UUID routeSaleId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        OffsetDateTime originalDate = OffsetDateTime.now().minusDays(2);
        RouteSale routeSale = RouteSale.builder()
                .id(routeSaleId)
                .invoiceNumber(1L)
                .userId(UUID.randomUUID())
                .clientId(UUID.randomUUID())
                .driverId(UUID.randomUUID())
                .total(new BigDecimal("100.00"))
                .createdAt(originalDate.minusDays(1))
                .build();
        RouteSalePayment historical = RouteSalePayment.builder()
                .id(paymentId)
                .routeSale(routeSale)
                .method(Sale.PaymentMethod.CASH)
                .amount(new BigDecimal("25.00"))
                .createdAt(originalDate)
                .build();
        routeSale.getPayments().add(historical);

        when(routeSaleRepository.findByIdWithDetails(routeSaleId)).thenReturn(Optional.of(routeSale));
        when(routeSaleRepository.saveAndFlush(any(RouteSale.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.savePayments(routeSaleId, List.of(
                new CreateRouteSalePaymentRequest(paymentId, Sale.PaymentMethod.CASH, new BigDecimal("25.00")),
                new CreateRouteSalePaymentRequest(Sale.PaymentMethod.CARD, new BigDecimal("25.00"))));

        assertThat(routeSale.getPayments()).hasSize(2);
        assertThat(historical.getCreatedAt()).isEqualTo(originalDate);
        assertThat(historical.getAmount()).isEqualByComparingTo("25.00");
        assertThat(response.status()).isEqualTo(RouteSale.RouteStatus.PARTIAL);
        assertThat(response.payments()).extracting(payment -> payment.amount())
                .containsExactlyInAnyOrder(new BigDecimal("25.00"), new BigDecimal("25.00"));
    }
}
