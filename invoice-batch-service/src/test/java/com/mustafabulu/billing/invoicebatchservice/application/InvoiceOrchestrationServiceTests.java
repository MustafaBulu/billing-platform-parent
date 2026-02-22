package com.mustafabulu.billing.invoicebatchservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.billing.invoicebatchservice.api.dto.GenerateInvoiceRequest;
import com.mustafabulu.billing.invoicebatchservice.application.dto.InvoiceOrchestrationResult;
import com.mustafabulu.billing.invoicebatchservice.domain.Invoice;
import com.mustafabulu.billing.invoicebatchservice.persistence.InboxRecordDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.InboxRecordRepository;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationRecordDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationRecordRepository;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationStatus;
import com.mustafabulu.billing.invoicebatchservice.persistence.OutboxEventDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.OutboxEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class InvoiceOrchestrationServiceTests {

    private static final String TENANT = "tenant-1";
    private static final String OPERATION_CODE = "INVOICE_GENERATE_AND_SETTLE";

    private final InvoiceGenerationService invoiceGenerationService = Mockito.mock(InvoiceGenerationService.class);
    private final InboxRecordRepository inboxRecordRepository = Mockito.mock(InboxRecordRepository.class);
    private final OrchestrationRecordRepository orchestrationRecordRepository = Mockito.mock(OrchestrationRecordRepository.class);
    private final OutboxEventRepository outboxEventRepository = Mockito.mock(OutboxEventRepository.class);
    private final InvoiceOrchestrationService service = new InvoiceOrchestrationService(
            invoiceGenerationService,
            inboxRecordRepository,
            orchestrationRecordRepository,
            outboxEventRepository,
            new ObjectMapper());

    @Test
    void shouldCreatePaymentRequestedOutboxForNewOrchestration() {
        GenerateInvoiceRequest request = new GenerateInvoiceRequest(
                TENANT, "customer-1", "2026-02", "USD", List.of(new BigDecimal("12.50")), "idem-new");
        Invoice invoice = invoice("INV-new", new BigDecimal("12.50"));

        when(inboxRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(TENANT, OPERATION_CODE, "idem-new"))
                .thenReturn(Optional.empty());
        when(inboxRecordRepository.save(any(InboxRecordDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orchestrationRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(TENANT, OPERATION_CODE, "idem-new"))
                .thenReturn(Optional.empty());
        when(orchestrationRecordRepository.save(any(OrchestrationRecordDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceGenerationService.generate(request)).thenReturn(invoice);
        when(outboxEventRepository.save(any(OutboxEventDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InvoiceOrchestrationResult result = service.generateAndSettle(request);

        assertThat(result.invoice().invoiceId()).isEqualTo("INV-new");
        assertThat(result.payment()).isNull();
        assertThat(result.settlement()).isNull();

        ArgumentCaptor<OutboxEventDocument> outboxCaptor = ArgumentCaptor.forClass(OutboxEventDocument.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEventDocument saved = outboxCaptor.getValue();
        assertThat(saved.getEventType()).isEqualTo("PAYMENT_REQUESTED");
        assertThat(saved.getStatus()).isEqualTo("NEW");
        assertThat(saved.getPayload()).contains("invoiceId=INV-new");
    }

    @Test
    void shouldReplayCompletedOrchestrationWithoutGeneratingInvoice() {
        GenerateInvoiceRequest request = new GenerateInvoiceRequest(
                TENANT, "customer-1", "2026-02", "USD", List.of(new BigDecimal("15.00")), "idem-complete");
        InboxRecordDocument inbox = new InboxRecordDocument();
        inbox.setOrchestrationId("ORCH-1");
        OrchestrationRecordDocument orchestration = new OrchestrationRecordDocument();
        orchestration.setOrchestrationId("ORCH-1");
        orchestration.setTenantId(TENANT);
        orchestration.setOperationCode(OPERATION_CODE);
        orchestration.setIdempotencyKey("idem-complete");
        orchestration.setInvoiceId("INV-1");
        orchestration.setPaymentTransactionId("TX-1");
        orchestration.setSettlementSagaId("SAGA-1");
        orchestration.setStatus(OrchestrationStatus.SETTLEMENT_COMPLETED);
        orchestration.setUpdatedAt(Instant.parse("2026-02-21T00:00:00Z"));
        Invoice invoice = invoice("INV-1", new BigDecimal("15.00"));

        when(inboxRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(TENANT, OPERATION_CODE, "idem-complete"))
                .thenReturn(Optional.of(inbox));
        when(orchestrationRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(TENANT, OPERATION_CODE, "idem-complete"))
                .thenReturn(Optional.of(orchestration));
        when(invoiceGenerationService.findById("INV-1")).thenReturn(invoice);

        InvoiceOrchestrationResult result = service.generateAndSettle(request);

        assertThat(result.invoice().invoiceId()).isEqualTo("INV-1");
        assertThat(result.payment().transactionId()).isEqualTo("TX-1");
        assertThat(result.settlement().sagaId()).isEqualTo("SAGA-1");
        verify(invoiceGenerationService, never()).generate(any(GenerateInvoiceRequest.class));
        verify(outboxEventRepository, never()).save(any(OutboxEventDocument.class));
    }

    @Test
    void shouldUseGeneratedIdempotencyKeyWhenRequestDoesNotProvideOne() {
        GenerateInvoiceRequest request = new GenerateInvoiceRequest(
                TENANT, "customer-1", "2026-02", "USD", List.of(new BigDecimal("9.00")), null);
        Invoice invoice = invoice("INV-auto", new BigDecimal("9.00"));

        when(inboxRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(inboxRecordRepository.save(any(InboxRecordDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orchestrationRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(orchestrationRecordRepository.save(any(OrchestrationRecordDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceGenerationService.generate(request)).thenReturn(invoice);
        when(outboxEventRepository.save(any(OutboxEventDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.generateAndSettle(request);

        ArgumentCaptor<InboxRecordDocument> inboxCaptor = ArgumentCaptor.forClass(InboxRecordDocument.class);
        verify(inboxRecordRepository).save(inboxCaptor.capture());
        assertThat(inboxCaptor.getValue().getIdempotencyKey()).startsWith("invoice-orchestration-");
    }

    private static Invoice invoice(String invoiceId, BigDecimal amount) {
        return new Invoice(
                invoiceId,
                TENANT,
                "customer-1",
                "2026-02",
                amount,
                "USD",
                "GENERATED",
                Instant.parse("2026-02-21T00:00:00Z"));
    }
}
