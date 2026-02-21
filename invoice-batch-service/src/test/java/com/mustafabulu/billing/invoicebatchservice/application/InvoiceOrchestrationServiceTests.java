package com.mustafabulu.billing.invoicebatchservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
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
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.client.RestClientException;

class InvoiceOrchestrationServiceTests {

    private final InvoiceGenerationService invoiceGenerationService = Mockito.mock(InvoiceGenerationService.class);
    private final InboxRecordRepository inboxRecordRepository = Mockito.mock(InboxRecordRepository.class);
    private final OrchestrationRecordRepository orchestrationRecordRepository = Mockito.mock(OrchestrationRecordRepository.class);
    private final OutboxEventRepository outboxEventRepository = Mockito.mock(OutboxEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReplayCompletedOrchestrationWithoutRegeneratingInvoice() throws Exception {
        HttpServer paymentServer = startServer("/api/v1/payments/process", 200, """
                {"transactionId":"TX-1","invoiceId":"INV-1","amount":12.5,"currency":"USD","status":"SUCCESS","providerReference":"APPROVED-1","processedAt":"2026-02-21T00:00:00Z"}
                """);
        HttpServer settlementServer = startServer("/api/v1/settlements/start", 200, """
                {"sagaId":"SAGA-1","tenantId":"tenant-1","invoiceId":"INV-1","paymentTransactionId":"TX-1","amount":12.5,"currency":"USD","status":"SETTLED","transitions":["STARTED","PAYMENT_CONFIRMED","SETTLED"],"updatedAt":"2026-02-21T00:00:00Z"}
                """);
        try {
            InvoiceOrchestrationService service = new InvoiceOrchestrationService(
                    invoiceGenerationService,
                    inboxRecordRepository,
                    orchestrationRecordRepository,
                    outboxEventRepository,
                    objectMapper,
                    "http://localhost:" + paymentServer.getAddress().getPort(),
                    "http://localhost:" + settlementServer.getAddress().getPort());

            GenerateInvoiceRequest request = new GenerateInvoiceRequest(
                    "tenant-1", "customer-1", "2026-02", "USD", List.of(new BigDecimal("12.50")), "idem-1");
            InboxRecordDocument inbox = new InboxRecordDocument();
            inbox.setOrchestrationId("ORCH-1");
            OrchestrationRecordDocument orchestration = new OrchestrationRecordDocument();
            orchestration.setOrchestrationId("ORCH-1");
            orchestration.setIdempotencyKey("idem-1");
            orchestration.setInvoiceId("INV-1");
            orchestration.setPaymentTransactionId("TX-1");
            orchestration.setStatus(OrchestrationStatus.SETTLEMENT_COMPLETED);
            Invoice invoice = new Invoice("INV-1", "tenant-1", "customer-1", "2026-02",
                    new BigDecimal("12.50"), "USD", "GENERATED", Instant.parse("2026-02-21T00:00:00Z"));

            when(inboxRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                    "tenant-1", "INVOICE_GENERATE_AND_SETTLE", "idem-1")).thenReturn(Optional.of(inbox));
            when(orchestrationRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                    "tenant-1", "INVOICE_GENERATE_AND_SETTLE", "idem-1")).thenReturn(Optional.of(orchestration));
            when(invoiceGenerationService.findById("INV-1")).thenReturn(invoice);

            InvoiceOrchestrationResult result = service.generateAndSettle(request);

            assertThat(result.invoice().invoiceId()).isEqualTo("INV-1");
            assertThat(result.payment().transactionId()).isEqualTo("TX-1");
            assertThat(result.settlement().status()).isEqualTo("SETTLED");
            verify(invoiceGenerationService, never()).generate(any(GenerateInvoiceRequest.class));
            verify(outboxEventRepository, never()).save(any(OutboxEventDocument.class));
        } finally {
            paymentServer.stop(0);
            settlementServer.stop(0);
        }
    }

    @Test
    void shouldMarkCompensationRequiredWhenPaymentCallFails() {
        InvoiceOrchestrationService service = new InvoiceOrchestrationService(
                invoiceGenerationService,
                inboxRecordRepository,
                orchestrationRecordRepository,
                outboxEventRepository,
                objectMapper,
                "http://127.0.0.1:1",
                "http://127.0.0.1:1");

        GenerateInvoiceRequest request = new GenerateInvoiceRequest(
                "tenant-1", "customer-1", "2026-02", "USD", List.of(new BigDecimal("5.00")), "idem-fail");
        when(inboxRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                "tenant-1", "INVOICE_GENERATE_AND_SETTLE", "idem-fail")).thenReturn(Optional.empty());
        when(inboxRecordRepository.save(any(InboxRecordDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(orchestrationRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                "tenant-1", "INVOICE_GENERATE_AND_SETTLE", "idem-fail")).thenReturn(Optional.empty());
        when(orchestrationRecordRepository.save(any(OrchestrationRecordDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEventDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(invoiceGenerationService.generate(any(GenerateInvoiceRequest.class))).thenReturn(
                new Invoice("INV-fail", "tenant-1", "customer-1", "2026-02",
                        new BigDecimal("5.00"), "USD", "GENERATED", Instant.now()));

        assertThatThrownBy(() -> service.generateAndSettle(request)).isInstanceOf(RestClientException.class);

        ArgumentCaptor<OrchestrationRecordDocument> orchestrationCaptor =
                ArgumentCaptor.forClass(OrchestrationRecordDocument.class);
        verify(orchestrationRecordRepository, atLeastOnce()).save(orchestrationCaptor.capture());
        assertThat(orchestrationCaptor.getAllValues())
                .anyMatch(saved -> saved.getStatus() == OrchestrationStatus.COMPENSATION_REQUIRED
                        && saved.getFailureReason() != null
                        && saved.getFailureReason().startsWith("PAYMENT_CALL_FAILED"));

        ArgumentCaptor<OutboxEventDocument> outboxCaptor = ArgumentCaptor.forClass(OutboxEventDocument.class);
        verify(outboxEventRepository, atLeastOnce()).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getAllValues()).anyMatch(event -> "ORCHESTRATION_FAILED".equals(event.getEventType()));
    }

    private static HttpServer startServer(String path, int statusCode, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            byte[] bytes = body.trim().getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
        server.start();
        return server;
    }
}
