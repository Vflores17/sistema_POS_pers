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
import com.vflores.pos.shared.application.PriceOverrideGuard;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteSaleServiceReplacePaymentsTest {

    private final RouteSaleRepository routeSaleRepository = mock(RouteSaleRepository.class);
    private final RouteSaleService service = new RouteSaleService(routeSaleRepository,
            mock(RouteSalePaymentRepository.class), mock(ProductRepository.class),
            mock(ClientRepository.class), mock(DriverRepository.class),
            mock(ProductPriceRepository.class), mock(PriceOverrideGuard.class));

    private UUID routeSaleId;
    private UUID firstPaymentId;
    private UUID secondPaymentId;
    private OffsetDateTime originalCreatedAt;
    private RouteSale routeSale;
    private RouteSalePayment firstPayment;
    private RouteSalePayment secondPayment;

    @BeforeEach
    void setUp() {
        routeSaleId = UUID.randomUUID();
        firstPaymentId = UUID.randomUUID();
        secondPaymentId = UUID.randomUUID();
        originalCreatedAt = OffsetDateTime.now().minusDays(5);
        routeSale = RouteSale.builder()
                .id(routeSaleId)
                .invoiceNumber(1L)
                .userId(UUID.randomUUID())
                .clientId(UUID.randomUUID())
                .driverId(UUID.randomUUID())
                .total(new BigDecimal("100.00"))
                .createdAt(OffsetDateTime.now().minusDays(6))
                .status(RouteSale.RouteStatus.PARTIAL)
                .build();
        firstPayment = RouteSalePayment.builder().id(firstPaymentId).routeSale(routeSale)
                .method(Sale.PaymentMethod.CASH).amount(new BigDecimal("40.00")).createdAt(originalCreatedAt).build();
        secondPayment = RouteSalePayment.builder().id(secondPaymentId).routeSale(routeSale)
                .method(Sale.PaymentMethod.SINPE).amount(new BigDecimal("40.00")).createdAt(originalCreatedAt).build();
        routeSale.getPayments().add(firstPayment);
        routeSale.getPayments().add(secondPayment);
        when(routeSaleRepository.findByIdWithDetails(routeSaleId)).thenReturn(Optional.of(routeSale));
        when(routeSaleRepository.saveAndFlush(any(RouteSale.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void modifiesAmountAndMethodOfReferencedPaymentsPreservingCreatedAt() {
        var response = service.replacePayments(routeSaleId, List.of(
                new CreateRouteSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.TRANSFER, new BigDecimal("55.00")),
                new CreateRouteSalePaymentRequest(secondPaymentId, Sale.PaymentMethod.CARD, new BigDecimal("45.00"))));

        assertThat(firstPayment.getAmount()).isEqualByComparingTo("55.00");
        assertThat(firstPayment.getMethod()).isEqualTo(Sale.PaymentMethod.TRANSFER);
        assertThat(secondPayment.getAmount()).isEqualByComparingTo("45.00");
        assertThat(secondPayment.getMethod()).isEqualTo(Sale.PaymentMethod.CARD);
        assertThat(firstPayment.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(secondPayment.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(routeSale.getPayments()).hasSize(2);
        assertThat(response.status()).isEqualTo(RouteSale.RouteStatus.PAID);
        assertThat(response.payments()).extracting(payment -> payment.amount())
                .containsExactlyInAnyOrder(new BigDecimal("55.00"), new BigDecimal("45.00"));
    }

    @Test
    void addsNewPaymentNextToReferencedOnes() {
        var response = service.replacePayments(routeSaleId, List.of(
                new CreateRouteSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.CASH, new BigDecimal("40.00")),
                new CreateRouteSalePaymentRequest(secondPaymentId, Sale.PaymentMethod.SINPE, new BigDecimal("40.00")),
                new CreateRouteSalePaymentRequest(Sale.PaymentMethod.CARD, new BigDecimal("20.00"))));

        assertThat(routeSale.getPayments()).hasSize(3);
        assertThat(firstPayment.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(response.status()).isEqualTo(RouteSale.RouteStatus.PAID);
        assertThat(response.payments()).extracting(payment -> payment.amount())
                .containsExactlyInAnyOrder(new BigDecimal("40.00"), new BigDecimal("40.00"), new BigDecimal("20.00"));
    }

    @Test
    void removesPaymentsNotReferencedInRequest() {
        var response = service.replacePayments(routeSaleId, List.of(
                new CreateRouteSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.CASH, new BigDecimal("40.00"))));

        assertThat(routeSale.getPayments()).singleElement().isSameAs(firstPayment);
        assertThat(response.status()).isEqualTo(RouteSale.RouteStatus.PARTIAL);
    }

    @Test
    void emptyRequestRemovesAllPaymentsAndReturnsToPending() {
        var response = service.replacePayments(routeSaleId, List.of());

        assertThat(routeSale.getPayments()).isEmpty();
        assertThat(response.status()).isEqualTo(RouteSale.RouteStatus.PENDING);
        assertThat(response.payments()).isEmpty();
    }

    @Test
    void mixedModifyAddAndDeleteResolvesFinalState() {
        var response = service.replacePayments(routeSaleId, List.of(
                new CreateRouteSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.TRANSFER, new BigDecimal("30.00")),
                new CreateRouteSalePaymentRequest(Sale.PaymentMethod.CARD, new BigDecimal("10.00"))));

        assertThat(routeSale.getPayments()).hasSize(2);
        assertThat(firstPayment.getAmount()).isEqualByComparingTo("30.00");
        assertThat(firstPayment.getMethod()).isEqualTo(Sale.PaymentMethod.TRANSFER);
        assertThat(firstPayment.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(routeSale.getPayments().stream().noneMatch(payment -> secondPaymentId.equals(payment.getId()))).isTrue();
        assertThat(response.status()).isEqualTo(RouteSale.RouteStatus.PARTIAL);
    }

    @Test
    void fullPaymentRecomputesStatusToPaidWhenSumEqualsTotal() {
        var response = service.replacePayments(routeSaleId, List.of(
                new CreateRouteSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.CASH, new BigDecimal("60.00")),
                new CreateRouteSalePaymentRequest(secondPaymentId, Sale.PaymentMethod.SINPE, new BigDecimal("40.00"))));

        assertThat(response.status()).isEqualTo(RouteSale.RouteStatus.PAID);
    }

    @Test
    void missingRouteSaleIsRejectedBeforeAnyMutation() {
        when(routeSaleRepository.findByIdWithDetails(routeSaleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replacePayments(routeSaleId, List.of()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(routeSaleRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelledRouteSaleCannotBeModified() {
        routeSale.setStatus(RouteSale.RouteStatus.CANCELLED);

        assertThatThrownBy(() -> service.replacePayments(routeSaleId, List.of()))
                .isInstanceOf(ConflictException.class);
        verify(routeSaleRepository, never()).saveAndFlush(any());
    }

    @Test
    void sumExceedingTotalIsRejectedBeforeAnyMutation() {
        assertThatThrownBy(() -> service.replacePayments(routeSaleId, List.of(
                new CreateRouteSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.CASH, new BigDecimal("40.00")),
                new CreateRouteSalePaymentRequest(secondPaymentId, Sale.PaymentMethod.SINPE, new BigDecimal("70.00")))))
                .isInstanceOf(ConflictException.class);

        assertThat(firstPayment.getAmount()).isEqualByComparingTo("40.00");
        assertThat(secondPayment.getAmount()).isEqualByComparingTo("40.00");
        assertThat(routeSale.getPayments()).hasSize(2);
        verify(routeSaleRepository, never()).saveAndFlush(any());
    }

    @Test
    void newPaymentThatWouldExceedTotalIsRejectedBeforeBeingAdded() {
        assertThatThrownBy(() -> service.replacePayments(routeSaleId, List.of(
                new CreateRouteSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.CASH, new BigDecimal("40.00")),
                new CreateRouteSalePaymentRequest(secondPaymentId, Sale.PaymentMethod.SINPE, new BigDecimal("40.00")),
                new CreateRouteSalePaymentRequest(Sale.PaymentMethod.CARD, new BigDecimal("30.00")))))
                .isInstanceOf(ConflictException.class);

        assertThat(routeSale.getPayments()).hasSize(2);
        verify(routeSaleRepository, never()).saveAndFlush(any());
    }

    @Test
    void nonPositiveAndNullAmountsAreRejected() {
        assertThatThrownBy(() -> service.replacePayments(routeSaleId, List.of(
                new CreateRouteSalePaymentRequest(Sale.PaymentMethod.CASH, BigDecimal.ZERO))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.replacePayments(routeSaleId, List.of(
                new CreateRouteSalePaymentRequest(Sale.PaymentMethod.CASH, new BigDecimal("-1.00")))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.replacePayments(routeSaleId, List.of(
                new CreateRouteSalePaymentRequest(Sale.PaymentMethod.CASH, null))))
                .isInstanceOf(ConflictException.class);
        verify(routeSaleRepository, never()).saveAndFlush(any());
    }

    @Test
    void paymentIdFromAnotherRouteSaleIsRejected() {
        assertThatThrownBy(() -> service.replacePayments(routeSaleId, List.of(
                new CreateRouteSalePaymentRequest(UUID.randomUUID(), Sale.PaymentMethod.CASH, new BigDecimal("10.00")))))
                .isInstanceOf(ConflictException.class);

        assertThat(routeSale.getPayments()).hasSize(2);
        verify(routeSaleRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicatePaymentReferencesAreRejected() {
        assertThatThrownBy(() -> service.replacePayments(routeSaleId, List.of(
                new CreateRouteSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.CASH, new BigDecimal("40.00")),
                new CreateRouteSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.CARD, new BigDecimal("40.00")))))
                .isInstanceOf(ConflictException.class);

        assertThat(routeSale.getPayments()).hasSize(2);
        verify(routeSaleRepository, never()).saveAndFlush(any());
    }

    @Test
    void replacedPaymentGetsFullyPersistedThroughSaveAndFlush() {
        service.replacePayments(routeSaleId, List.of(
                new CreateRouteSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.TRANSFER, new BigDecimal("60.00")),
                new CreateRouteSalePaymentRequest(secondPaymentId, Sale.PaymentMethod.CASH, new BigDecimal("40.00"))));

        verify(routeSaleRepository).saveAndFlush(routeSale);
    }

    @Test
    void replacementBoundaryIsTransactional() throws NoSuchMethodException {
        Method method = RouteSaleService.class.getMethod(
                "replacePayments", UUID.class, List.class
        );

        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }
}