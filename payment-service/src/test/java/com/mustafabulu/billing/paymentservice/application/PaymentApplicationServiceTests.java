package com.mustafabulu.billing.paymentservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafabulu.billing.common.idempotency.IdempotencyContextHolder;
import com.mustafabulu.billing.common.tenant.TenantContextHolder;
import com.mustafabulu.billing.paymentservice.api.dto.ProcessPaymentRequest;
import com.mustafabulu.billing.paymentservice.domain.PaymentResult;
import com.mustafabulu.billing.paymentservice.persistence.PaymentRecordDocument;
import com.mustafabulu.billing.paymentservice.persistence.PaymentRecordRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

class PaymentApplicationServiceTests {

    private final BankSoapGatewayMock bankSoapGatewayMock = Mockito.mock(BankSoapGatewayMock.class);
    private final PaymentRecordRepository paymentRecordRepository = Mockito.mock(PaymentRecordRepository.class);
    private final PaymentApplicationService paymentApplicationService =
            new PaymentApplicationService(bankSoapGatewayMock, paymentRecordRepository);

    @AfterEach
    void cleanupContext() {
        TenantContextHolder.clear();
        IdempotencyContextHolder.clear();
    }

    @Test
    void shouldReturnExistingRecordWithoutCallingProvider() {
        ProcessPaymentRequest request = new ProcessPaymentRequest(
                "tenant-1", "INV-1", "idem-1", new BigDecimal("12.00"), "USD");
        PaymentRecordDocument existing = persisted(
                "tenant-1", "tenant-1:PAYMENT_PROCESS:idem-1", "INV-1", "SUCCESS", "APPROVED-old");

        when(paymentRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                "tenant-1", "PAYMENT_PROCESS", "tenant-1:PAYMENT_PROCESS:idem-1")).thenReturn(Optional.of(existing));

        PaymentResult result = paymentApplicationService.process(request);

        assertThat(result.transactionId()).isEqualTo(existing.getTransactionId());
        assertThat(result.status()).isEqualTo("SUCCESS");
        verify(bankSoapGatewayMock, never()).authorize(any(), any(), any());
        verify(paymentRecordRepository, never()).save(any(PaymentRecordDocument.class));
    }

    @Test
    void shouldPersistSuccessForApprovedProviderResponse() {
        ProcessPaymentRequest request = new ProcessPaymentRequest(
                "tenant-1", "INV-2", "idem-2", new BigDecimal("20.00"), "USD");
        when(paymentRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                "tenant-1", "PAYMENT_PROCESS", "tenant-1:PAYMENT_PROCESS:idem-2")).thenReturn(Optional.empty());
        when(bankSoapGatewayMock.authorize(new BigDecimal("20.00"), "USD", "INV-2"))
                .thenReturn("APPROVED-USD-INV-2-ref");
        when(paymentRecordRepository.save(any(PaymentRecordDocument.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResult result = paymentApplicationService.process(request);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.providerReference()).startsWith("APPROVED");
    }

    @Test
    void shouldPersistFailedForDeclinedProviderResponse() {
        ProcessPaymentRequest request = new ProcessPaymentRequest(
                "tenant-1", "INV-3", "idem-3", new BigDecimal("20000.00"), "USD");
        when(paymentRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                "tenant-1", "PAYMENT_PROCESS", "tenant-1:PAYMENT_PROCESS:idem-3")).thenReturn(Optional.empty());
        when(bankSoapGatewayMock.authorize(new BigDecimal("20000.00"), "USD", "INV-3"))
                .thenReturn("DECLINED-ref");
        when(paymentRecordRepository.save(any(PaymentRecordDocument.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResult result = paymentApplicationService.process(request);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.providerReference()).startsWith("DECLINED");
    }

    @Test
    void shouldFallbackToExistingRecordWhenSaveHitsDuplicate() {
        ProcessPaymentRequest request = new ProcessPaymentRequest(
                "tenant-1", "INV-4", "idem-4", new BigDecimal("11.00"), "USD");
        PaymentRecordDocument existing = persisted(
                "tenant-1", "tenant-1:PAYMENT_PROCESS:idem-4", "INV-4", "SUCCESS", "APPROVED-existing");

        when(paymentRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                "tenant-1", "PAYMENT_PROCESS", "tenant-1:PAYMENT_PROCESS:idem-4"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(bankSoapGatewayMock.authorize(new BigDecimal("11.00"), "USD", "INV-4")).thenReturn("APPROVED-new");
        when(paymentRecordRepository.save(any(PaymentRecordDocument.class))).thenThrow(new DuplicateKeyException("dup"));

        PaymentResult result = paymentApplicationService.process(request);

        assertThat(result.transactionId()).isEqualTo(existing.getTransactionId());
        assertThat(result.providerReference()).isEqualTo("APPROVED-existing");
    }

    @Test
    void shouldUseTenantAndIdempotencyFromContextWhenPresent() {
        ProcessPaymentRequest request = new ProcessPaymentRequest(
                "tenant-from-request", "INV-5", "idem-request", new BigDecimal("9.00"), "USD");
        TenantContextHolder.setTenantId("tenant-from-header");
        IdempotencyContextHolder.setIdempotencyKey("idem-from-header");
        PaymentRecordDocument existing = persisted(
                "tenant-from-header", "tenant-from-header:PAYMENT_PROCESS:idem-from-header",
                "INV-5", "SUCCESS", "APPROVED-context");
        when(paymentRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                "tenant-from-header", "PAYMENT_PROCESS", "tenant-from-header:PAYMENT_PROCESS:idem-from-header"))
                .thenReturn(Optional.of(existing));

        PaymentResult result = paymentApplicationService.process(request);

        assertThat(result.providerReference()).isEqualTo("APPROVED-context");
    }

    private static PaymentRecordDocument persisted(String tenantId,
                                                   String idempotencyKey,
                                                   String invoiceId,
                                                   String status,
                                                   String providerReference) {
        PaymentRecordDocument document = new PaymentRecordDocument();
        document.setTenantId(tenantId);
        document.setOperationCode("PAYMENT_PROCESS");
        document.setIdempotencyKey(idempotencyKey);
        document.setTransactionId("TX-" + invoiceId);
        document.setInvoiceId(invoiceId);
        document.setAmount(new BigDecimal("10.00"));
        document.setCurrency("USD");
        document.setStatus(status);
        document.setProviderReference(providerReference);
        document.setProcessedAt(Instant.parse("2026-02-21T00:00:00Z"));
        return document;
    }
}
