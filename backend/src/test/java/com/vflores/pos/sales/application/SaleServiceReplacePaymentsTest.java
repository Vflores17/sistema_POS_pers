package com.vflores.pos.sales.application;

import com.vflores.pos.clients.domain.repository.ClientRepository;
import com.vflores.pos.products.domain.repository.ProductPriceRepository;
import com.vflores.pos.products.domain.repository.ProductRepository;
import com.vflores.pos.sales.api.dto.CreateSalePaymentRequest;
import com.vflores.pos.sales.domain.model.Sale;
import com.vflores.pos.sales.domain.model.SalePayment;
import com.vflores.pos.sales.domain.repository.SalePaymentRepository;
import com.vflores.pos.sales.domain.repository.SaleRepository;
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

class SaleServiceReplacePaymentsTest {

    private final SaleRepository saleRepository = mock(SaleRepository.class);
    private final SaleService service = new SaleService(saleRepository, mock(ProductRepository.class),
            mock(ClientRepository.class), mock(ProductPriceRepository.class),
            mock(SalePaymentRepository.class), mock(PriceOverrideGuard.class));

    private UUID saleId;
    private UUID firstPaymentId;
    private UUID secondPaymentId;
    private OffsetDateTime originalCreatedAt;
    private Sale sale;
    private SalePayment firstPayment;
    private SalePayment secondPayment;

    @BeforeEach
    void setUp() {
        saleId = UUID.randomUUID();
        firstPaymentId = UUID.randomUUID();
        secondPaymentId = UUID.randomUUID();
        originalCreatedAt = OffsetDateTime.now().minusDays(5);
        sale = Sale.builder()
                .id(saleId)
                .invoiceNumber(1L)
                .userId(UUID.randomUUID())
                .clientId(UUID.randomUUID())
                .total(new BigDecimal("100.00"))
                .createdAt(OffsetDateTime.now().minusDays(6))
                .status(Sale.SaleStatus.PARTIAL)
                .build();
        firstPayment = SalePayment.builder().id(firstPaymentId).sale(sale)
                .method(Sale.PaymentMethod.CASH).amount(new BigDecimal("40.00")).createdAt(originalCreatedAt).build();
        secondPayment = SalePayment.builder().id(secondPaymentId).sale(sale)
                .method(Sale.PaymentMethod.SINPE).amount(new BigDecimal("40.00")).createdAt(originalCreatedAt).build();
        sale.getPayments().add(firstPayment);
        sale.getPayments().add(secondPayment);
        when(saleRepository.findByIdWithDetails(saleId)).thenReturn(Optional.of(sale));
        when(saleRepository.saveAndFlush(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void modifiesAmountAndMethodOfReferencedPaymentsPreservingCreatedAt() {
        var response = service.replacePayments(saleId, List.of(
                new CreateSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.TRANSFER, new BigDecimal("55.00")),
                new CreateSalePaymentRequest(secondPaymentId, Sale.PaymentMethod.CARD, new BigDecimal("45.00"))));

        assertThat(firstPayment.getAmount()).isEqualByComparingTo("55.00");
        assertThat(firstPayment.getMethod()).isEqualTo(Sale.PaymentMethod.TRANSFER);
        assertThat(secondPayment.getAmount()).isEqualByComparingTo("45.00");
        assertThat(secondPayment.getMethod()).isEqualTo(Sale.PaymentMethod.CARD);
        assertThat(firstPayment.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(secondPayment.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(sale.getPayments()).hasSize(2);
        assertThat(response.status()).isEqualTo(Sale.SaleStatus.PAID);
        assertThat(response.payments()).extracting(payment -> payment.amount())
                .containsExactlyInAnyOrder(new BigDecimal("55.00"), new BigDecimal("45.00"));
    }

    @Test
    void addsNewPaymentNextToReferencedOnes() {
        var response = service.replacePayments(saleId, List.of(
                new CreateSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.CASH, new BigDecimal("40.00")),
                new CreateSalePaymentRequest(secondPaymentId, Sale.PaymentMethod.SINPE, new BigDecimal("40.00")),
                new CreateSalePaymentRequest(Sale.PaymentMethod.CARD, new BigDecimal("20.00"))));

        assertThat(sale.getPayments()).hasSize(3);
        assertThat(firstPayment.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(response.status()).isEqualTo(Sale.SaleStatus.PAID);
        assertThat(response.payments()).extracting(payment -> payment.amount())
                .containsExactlyInAnyOrder(new BigDecimal("40.00"), new BigDecimal("40.00"), new BigDecimal("20.00"));
    }

    @Test
    void removesPaymentsNotReferencedInRequest() {
        var response = service.replacePayments(saleId, List.of(
                new CreateSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.CASH, new BigDecimal("40.00"))));

        assertThat(sale.getPayments()).singleElement().isSameAs(firstPayment);
        assertThat(response.status()).isEqualTo(Sale.SaleStatus.PARTIAL);
    }

    @Test
    void emptyRequestRemovesAllPaymentsAndReturnsToPending() {
        var response = service.replacePayments(saleId, List.of());

        assertThat(sale.getPayments()).isEmpty();
        assertThat(response.status()).isEqualTo(Sale.SaleStatus.PENDING);
        assertThat(response.payments()).isEmpty();
    }

    @Test
    void mixedModifyAddAndDeleteResolvesFinalState() {
        var response = service.replacePayments(saleId, List.of(
                new CreateSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.TRANSFER, new BigDecimal("30.00")),
                new CreateSalePaymentRequest(Sale.PaymentMethod.CARD, new BigDecimal("10.00"))));

        assertThat(sale.getPayments()).hasSize(2);
        assertThat(firstPayment.getAmount()).isEqualByComparingTo("30.00");
        assertThat(firstPayment.getMethod()).isEqualTo(Sale.PaymentMethod.TRANSFER);
        assertThat(firstPayment.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(sale.getPayments().stream().noneMatch(payment -> secondPaymentId.equals(payment.getId()))).isTrue();
        assertThat(response.status()).isEqualTo(Sale.SaleStatus.PARTIAL);
    }

    @Test
    void fullPaymentRecomputesStatusToPaidWhenSumEqualsTotal() {
        var response = service.replacePayments(saleId, List.of(
                new CreateSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.CASH, new BigDecimal("60.00")),
                new CreateSalePaymentRequest(secondPaymentId, Sale.PaymentMethod.SINPE, new BigDecimal("40.00"))));

        assertThat(response.status()).isEqualTo(Sale.SaleStatus.PAID);
    }

    @Test
    void missingSaleIsRejectedBeforeAnyMutation() {
        when(saleRepository.findByIdWithDetails(saleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replacePayments(saleId, List.of()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(saleRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelledSaleCannotBeModified() {
        sale.setStatus(Sale.SaleStatus.CANCELLED);

        assertThatThrownBy(() -> service.replacePayments(saleId, List.of()))
                .isInstanceOf(ConflictException.class);
        verify(saleRepository, never()).saveAndFlush(any());
    }

    @Test
    void sumExceedingTotalIsRejectedBeforeAnyMutation() {
        assertThatThrownBy(() -> service.replacePayments(saleId, List.of(
                new CreateSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.CASH, new BigDecimal("40.00")),
                new CreateSalePaymentRequest(secondPaymentId, Sale.PaymentMethod.SINPE, new BigDecimal("70.00")))))
                .isInstanceOf(ConflictException.class);

        assertThat(firstPayment.getAmount()).isEqualByComparingTo("40.00");
        assertThat(secondPayment.getAmount()).isEqualByComparingTo("40.00");
        assertThat(sale.getPayments()).hasSize(2);
        verify(saleRepository, never()).saveAndFlush(any());
    }

    @Test
    void newPaymentThatWouldExceedTotalIsRejectedBeforeBeingAdded() {
        assertThatThrownBy(() -> service.replacePayments(saleId, List.of(
                new CreateSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.CASH, new BigDecimal("40.00")),
                new CreateSalePaymentRequest(secondPaymentId, Sale.PaymentMethod.SINPE, new BigDecimal("40.00")),
                new CreateSalePaymentRequest(Sale.PaymentMethod.CARD, new BigDecimal("30.00")))))
                .isInstanceOf(ConflictException.class);

        assertThat(sale.getPayments()).hasSize(2);
        verify(saleRepository, never()).saveAndFlush(any());
    }

    @Test
    void nonPositiveAndNullAmountsAreRejected() {
        assertThatThrownBy(() -> service.replacePayments(saleId, List.of(
                new CreateSalePaymentRequest(Sale.PaymentMethod.CASH, BigDecimal.ZERO))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.replacePayments(saleId, List.of(
                new CreateSalePaymentRequest(Sale.PaymentMethod.CASH, new BigDecimal("-1.00")))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.replacePayments(saleId, List.of(
                new CreateSalePaymentRequest(Sale.PaymentMethod.CASH, null))))
                .isInstanceOf(ConflictException.class);
        verify(saleRepository, never()).saveAndFlush(any());
    }

    @Test
    void paymentIdFromAnotherSaleIsRejected() {
        assertThatThrownBy(() -> service.replacePayments(saleId, List.of(
                new CreateSalePaymentRequest(UUID.randomUUID(), Sale.PaymentMethod.CASH, new BigDecimal("10.00")))))
                .isInstanceOf(ConflictException.class);

        assertThat(sale.getPayments()).hasSize(2);
        verify(saleRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicatePaymentReferencesAreRejected() {
        assertThatThrownBy(() -> service.replacePayments(saleId, List.of(
                new CreateSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.CASH, new BigDecimal("40.00")),
                new CreateSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.CARD, new BigDecimal("40.00")))))
                .isInstanceOf(ConflictException.class);

        assertThat(sale.getPayments()).hasSize(2);
        verify(saleRepository, never()).saveAndFlush(any());
    }

    @Test
    void replacedPaymentGetsFullyPersistedThroughSaveAndFlush() {
        service.replacePayments(saleId, List.of(
                new CreateSalePaymentRequest(firstPaymentId, Sale.PaymentMethod.TRANSFER, new BigDecimal("60.00")),
                new CreateSalePaymentRequest(secondPaymentId, Sale.PaymentMethod.CASH, new BigDecimal("40.00"))));

        verify(saleRepository).saveAndFlush(sale);
    }

    @Test
    void replacementBoundaryIsTransactional() throws NoSuchMethodException {
        Method method = SaleService.class.getMethod(
                "replacePayments", UUID.class, List.class
        );

        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }
}