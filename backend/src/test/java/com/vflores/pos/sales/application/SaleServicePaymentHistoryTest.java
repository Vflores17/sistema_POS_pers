package com.vflores.pos.sales.application;

import com.vflores.pos.clients.domain.repository.ClientRepository;
import com.vflores.pos.products.domain.repository.ProductPriceRepository;
import com.vflores.pos.products.domain.repository.ProductRepository;
import com.vflores.pos.sales.api.dto.CreateSalePaymentRequest;
import com.vflores.pos.sales.domain.model.Sale;
import com.vflores.pos.sales.domain.model.SalePayment;
import com.vflores.pos.sales.domain.repository.SalePaymentRepository;
import com.vflores.pos.sales.domain.repository.SaleRepository;
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

class SaleServicePaymentHistoryTest {

    @Test
    void returnsOnlyPaymentMovementsProvidedByTheRequestedPeriod() {
        SaleRepository saleRepository = mock(SaleRepository.class);
        SalePaymentRepository paymentRepository = mock(SalePaymentRepository.class);
        SaleService service = new SaleService(saleRepository, mock(ProductRepository.class),
                mock(ClientRepository.class), mock(ProductPriceRepository.class), paymentRepository);
        OffsetDateTime from = OffsetDateTime.now().minusHours(8);
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime saleCreatedAt = from.minusDays(2);
        Sale sale = Sale.builder().id(UUID.randomUUID()).invoiceNumber(14L)
                .clientId(UUID.randomUUID()).createdAt(saleCreatedAt).build();
        SalePayment payment = SalePayment.builder().id(UUID.randomUUID()).sale(sale)
                .method(Sale.PaymentMethod.TRANSFER).amount(new BigDecimal("15.00"))
                .createdAt(from.plusHours(1)).build();
        when(paymentRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAsc(from, to))
                .thenReturn(List.of(payment));

        var movements = service.findPaymentMovements(from, to);

        assertThat(movements).singleElement().satisfies(movement -> {
            assertThat(movement.saleId()).isEqualTo(sale.getId());
            assertThat(movement.invoiceNumber()).isEqualTo(14L);
            assertThat(movement.saleCreatedAt()).isEqualTo(saleCreatedAt);
            assertThat(movement.createdAt()).isEqualTo(payment.getCreatedAt());
        });
    }

    @Test
    void appendsNewPaymentWithoutReplacingHistoricalPayment() {
        SaleRepository saleRepository = mock(SaleRepository.class);
        SaleService service = new SaleService(saleRepository, mock(ProductRepository.class),
                mock(ClientRepository.class), mock(ProductPriceRepository.class),
                mock(SalePaymentRepository.class));

        UUID saleId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        OffsetDateTime originalDate = OffsetDateTime.now().minusDays(3);
        Sale sale = Sale.builder()
                .id(saleId)
                .invoiceNumber(1L)
                .userId(UUID.randomUUID())
                .clientId(UUID.randomUUID())
                .total(new BigDecimal("100.00"))
                .createdAt(originalDate.minusDays(1))
                .build();
        SalePayment historical = SalePayment.builder()
                .id(paymentId)
                .sale(sale)
                .method(Sale.PaymentMethod.CASH)
                .amount(new BigDecimal("40.00"))
                .createdAt(originalDate)
                .build();
        sale.getPayments().add(historical);

        when(saleRepository.findByIdWithDetails(saleId)).thenReturn(Optional.of(sale));
        when(saleRepository.saveAndFlush(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.savePayments(saleId, List.of(
                new CreateSalePaymentRequest(paymentId, Sale.PaymentMethod.CASH, new BigDecimal("40.00")),
                new CreateSalePaymentRequest(Sale.PaymentMethod.SINPE, new BigDecimal("20.00"))));

        assertThat(sale.getPayments()).hasSize(2);
        assertThat(historical.getCreatedAt()).isEqualTo(originalDate);
        assertThat(historical.getAmount()).isEqualByComparingTo("40.00");
        assertThat(response.status()).isEqualTo(Sale.SaleStatus.PARTIAL);
        assertThat(response.payments()).extracting(payment -> payment.amount())
                .containsExactlyInAnyOrder(new BigDecimal("40.00"), new BigDecimal("20.00"));
    }
}
