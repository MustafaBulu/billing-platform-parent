package com.mustafabulu.billing.invoicebatchservice.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.billing.common.idempotency.IdempotencyHeaders;
import com.mustafabulu.billing.common.tenant.TenantContextFilter;
import com.mustafabulu.billing.invoicebatchservice.api.dto.GenerateInvoiceRequest;
import com.mustafabulu.billing.invoicebatchservice.application.dto.InvoiceOrchestrationResult;
import com.mustafabulu.billing.invoicebatchservice.application.dto.PaymentProcessRequest;
import com.mustafabulu.billing.invoicebatchservice.application.dto.PaymentProcessResponse;
import com.mustafabulu.billing.invoicebatchservice.application.dto.SettlementResponse;
import com.mustafabulu.billing.invoicebatchservice.application.dto.StartSettlementRequest;
import com.mustafabulu.billing.invoicebatchservice.domain.Invoice;
import com.mustafabulu.billing.invoicebatchservice.persistence.InboxRecordDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.InboxRecordRepository;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationRecordDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationRecordRepository;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationStatus;
import com.mustafabulu.billing.invoicebatchservice.persistence.OutboxEventDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.OutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class InvoiceOrchestrationService {
    private static final String OPERATION_CODE = "INVOICE_GENERATE_AND_SETTLE";
    private static final String INBOX_STATUS_PROCESSING = "PROCESSING";
    private static final String INBOX_STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FIELD = "status";

    private final InvoiceGenerationService invoiceGenerationService;
    private final RestClient paymentRestClient;
    private final RestClient settlementRestClient;
    private final InboxRecordRepository inboxRecordRepository;
    private final OrchestrationRecordRepository orchestrationRecordRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public InvoiceOrchestrationService(InvoiceGenerationService invoiceGenerationService,
                                       InboxRecordRepository inboxRecordRepository,
                                       OrchestrationRecordRepository orchestrationRecordRepository,
                                       OutboxEventRepository outboxEventRepository,
                                       ObjectMapper objectMapper,
                                       @Value("${integration.payment.base-url}") String paymentBaseUrl,
                                       @Value("${integration.settlement.base-url}") String settlementBaseUrl) {
        this.invoiceGenerationService = invoiceGenerationService;
        this.inboxRecordRepository = inboxRecordRepository;
        this.orchestrationRecordRepository = orchestrationRecordRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.paymentRestClient = RestClient.builder().baseUrl(paymentBaseUrl).build();
        this.settlementRestClient = RestClient.builder().baseUrl(settlementBaseUrl).build();
    }

    public InvoiceOrchestrationResult generateAndSettle(GenerateInvoiceRequest request) {
        String tenantId = request.tenantId();
        String orchestrationIdempotencyKey = buildOrchestrationIdempotencyKey(request);
        String orchestrationId = ensureInbox(tenantId, orchestrationIdempotencyKey);
        OrchestrationRecordDocument orchestration = ensureOrchestrationRecord(
                tenantId, orchestrationIdempotencyKey, orchestrationId);

        if (orchestration.getStatus() == OrchestrationStatus.SETTLEMENT_COMPLETED) {
            return replayCompletedResult(orchestration, request);
        }

        Invoice invoice = invoiceGenerationService.generate(request);
        orchestration.setInvoiceId(invoice.invoiceId());
        orchestration.setStatus(OrchestrationStatus.INVOICE_GENERATED);
        orchestration.setUpdatedAt(Instant.now());
        orchestrationRecordRepository.save(orchestration);
        writeOutboxEvent(orchestration, "INVOICE_GENERATED", Map.of(
                "invoiceId", invoice.invoiceId(),
                "tenantId", tenantId));

        PaymentProcessResponse paymentResponse;
        try {
            paymentResponse = paymentRestClient.post()
                    .uri("/api/v1/payments/process")
                    .header(TenantContextFilter.TENANT_HEADER, tenantId)
                    .header(IdempotencyHeaders.IDEMPOTENCY_KEY, orchestrationIdempotencyKey)
                    .body(new PaymentProcessRequest(
                            tenantId,
                            invoice.invoiceId(),
                            orchestrationIdempotencyKey,
                            invoice.totalAmount(),
                            invoice.currency()))
                    .retrieve()
                    .body(PaymentProcessResponse.class);
        } catch (RestClientException exception) {
            markFailed(orchestration, "PAYMENT_CALL_FAILED: " + exception.getMessage());
            throw exception;
        }

        if (paymentResponse == null) {
            markFailed(orchestration, "PAYMENT_EMPTY_RESPONSE");
            throw new IllegalStateException("Payment response is empty");
        }

        orchestration.setPaymentTransactionId(paymentResponse.transactionId());
        orchestration.setStatus(OrchestrationStatus.PAYMENT_COMPLETED);
        orchestration.setUpdatedAt(Instant.now());
        orchestrationRecordRepository.save(orchestration);
        writeOutboxEvent(orchestration, "PAYMENT_COMPLETED", Map.of(
                "transactionId", paymentResponse.transactionId(),
                STATUS_FIELD, paymentResponse.status()));

        SettlementResponse settlementResponse;
        try {
            settlementResponse = settlementRestClient.post()
                    .uri("/api/v1/settlements/start")
                    .header(TenantContextFilter.TENANT_HEADER, tenantId)
                    .header(IdempotencyHeaders.IDEMPOTENCY_KEY, orchestrationIdempotencyKey)
                    .body(new StartSettlementRequest(
                            tenantId,
                            invoice.invoiceId(),
                            paymentResponse.transactionId(),
                            orchestrationIdempotencyKey,
                            invoice.totalAmount(),
                            invoice.currency(),
                            paymentResponse.status()))
                    .retrieve()
                    .body(SettlementResponse.class);
        } catch (RestClientException exception) {
            markFailed(orchestration, "SETTLEMENT_CALL_FAILED: " + exception.getMessage());
            throw exception;
        }

        if (settlementResponse == null) {
            markFailed(orchestration, "SETTLEMENT_EMPTY_RESPONSE");
            throw new IllegalStateException("Settlement response is empty");
        }

        orchestration.setSettlementSagaId(settlementResponse.sagaId());
        if ("SETTLED".equalsIgnoreCase(settlementResponse.status())) {
            orchestration.setStatus(OrchestrationStatus.SETTLEMENT_COMPLETED);
            orchestration.setFailureReason(null);
        } else {
            orchestration.setStatus(OrchestrationStatus.COMPENSATION_REQUIRED);
            orchestration.setFailureReason("SETTLEMENT_STATUS_" + settlementResponse.status());
        }
        orchestration.setUpdatedAt(Instant.now());
        orchestrationRecordRepository.save(orchestration);
        writeOutboxEvent(orchestration, "SETTLEMENT_COMPLETED", Map.of(
                "sagaId", settlementResponse.sagaId(),
                STATUS_FIELD, settlementResponse.status()));
        if (orchestration.getStatus() == OrchestrationStatus.SETTLEMENT_COMPLETED) {
            markInboxCompleted(tenantId, orchestrationIdempotencyKey, orchestration.getOrchestrationId());
        }

        return new InvoiceOrchestrationResult(invoice, paymentResponse, settlementResponse);
    }

    private String ensureInbox(String tenantId, String idempotencyKey) {
        InboxRecordDocument existing = inboxRecordRepository
                .findByTenantIdAndOperationCodeAndIdempotencyKey(tenantId, OPERATION_CODE, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            return existing.getOrchestrationId();
        }

        String orchestrationId = "ORCH-" + UUID.randomUUID();
        InboxRecordDocument created = new InboxRecordDocument();
        created.setTenantId(tenantId);
        created.setOperationCode(OPERATION_CODE);
        created.setIdempotencyKey(idempotencyKey);
        created.setOrchestrationId(orchestrationId);
        created.setStatus(INBOX_STATUS_PROCESSING);
        created.setCreatedAt(Instant.now());
        created.setUpdatedAt(Instant.now());
        try {
            return inboxRecordRepository.save(created).getOrchestrationId();
        } catch (DuplicateKeyException duplicateKeyException) {
            return inboxRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(tenantId, OPERATION_CODE, idempotencyKey)
                    .map(InboxRecordDocument::getOrchestrationId)
                    .orElseThrow(() -> duplicateKeyException);
        }
    }

    private OrchestrationRecordDocument ensureOrchestrationRecord(String tenantId,
                                                                  String idempotencyKey,
                                                                  String orchestrationId) {
        OrchestrationRecordDocument existing = orchestrationRecordRepository
                .findByTenantIdAndOperationCodeAndIdempotencyKey(tenantId, OPERATION_CODE, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        OrchestrationRecordDocument created = new OrchestrationRecordDocument();
        created.setOrchestrationId(orchestrationId);
        created.setTenantId(tenantId);
        created.setOperationCode(OPERATION_CODE);
        created.setIdempotencyKey(idempotencyKey);
        created.setStatus(OrchestrationStatus.RECEIVED);
        created.setCreatedAt(Instant.now());
        created.setUpdatedAt(Instant.now());
        try {
            return orchestrationRecordRepository.save(created);
        } catch (DuplicateKeyException duplicateKeyException) {
            return orchestrationRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                            tenantId, OPERATION_CODE, idempotencyKey)
                    .orElseThrow(() -> duplicateKeyException);
        }
    }

    private void markInboxCompleted(String tenantId, String idempotencyKey, String orchestrationId) {
        InboxRecordDocument inboxRecord = inboxRecordRepository
                .findByTenantIdAndOperationCodeAndIdempotencyKey(tenantId, OPERATION_CODE, idempotencyKey)
                .orElse(null);
        if (inboxRecord == null) {
            return;
        }
        inboxRecord.setStatus(INBOX_STATUS_COMPLETED);
        inboxRecord.setOrchestrationId(orchestrationId);
        inboxRecord.setUpdatedAt(Instant.now());
        inboxRecordRepository.save(inboxRecord);
    }

    private void markFailed(OrchestrationRecordDocument orchestration, String reason) {
        orchestration.setStatus(OrchestrationStatus.COMPENSATION_REQUIRED);
        orchestration.setFailureReason(reason);
        orchestration.setUpdatedAt(Instant.now());
        orchestrationRecordRepository.save(orchestration);
        writeOutboxEvent(orchestration, "ORCHESTRATION_FAILED", Map.of(
                STATUS_FIELD, OrchestrationStatus.COMPENSATION_REQUIRED.name(),
                "reason", reason));
    }

    private void writeOutboxEvent(OrchestrationRecordDocument orchestration, String eventType, Map<String, Object> payload) {
        OutboxEventDocument event = new OutboxEventDocument();
        event.setEventId("EVT-" + UUID.randomUUID());
        event.setOrchestrationId(orchestration.getOrchestrationId());
        event.setEventType(eventType);
        event.setStatus("NEW");
        event.setCreatedAt(Instant.now());
        event.setPayload(toJson(payload));
        outboxEventRepository.save(event);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return payload.toString();
        }
    }

    private InvoiceOrchestrationResult replayCompletedResult(OrchestrationRecordDocument orchestration,
                                                             GenerateInvoiceRequest request) {
        Invoice invoice = invoiceGenerationService.findById(orchestration.getInvoiceId());
        if (invoice == null) {
            invoice = invoiceGenerationService.generate(request);
        }

        PaymentProcessResponse payment = paymentRestClient.post()
                .uri("/api/v1/payments/process")
                .header(TenantContextFilter.TENANT_HEADER, request.tenantId())
                .header(IdempotencyHeaders.IDEMPOTENCY_KEY, orchestration.getIdempotencyKey())
                .body(new PaymentProcessRequest(
                        request.tenantId(),
                        invoice.invoiceId(),
                        orchestration.getIdempotencyKey(),
                        invoice.totalAmount(),
                        invoice.currency()))
                .retrieve()
                .body(PaymentProcessResponse.class);

        SettlementResponse settlement = settlementRestClient.post()
                .uri("/api/v1/settlements/start")
                .header(TenantContextFilter.TENANT_HEADER, request.tenantId())
                .header(IdempotencyHeaders.IDEMPOTENCY_KEY, orchestration.getIdempotencyKey())
                .body(new StartSettlementRequest(
                        request.tenantId(),
                        invoice.invoiceId(),
                        payment != null ? payment.transactionId() : orchestration.getPaymentTransactionId(),
                        orchestration.getIdempotencyKey(),
                        invoice.totalAmount(),
                        invoice.currency(),
                        payment != null ? payment.status() : "SUCCESS"))
                .retrieve()
                .body(SettlementResponse.class);

        return new InvoiceOrchestrationResult(invoice, payment, settlement);
    }

    private String buildOrchestrationIdempotencyKey(GenerateInvoiceRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            return request.idempotencyKey().trim();
        }

        String rawKey = request.tenantId() + "|" + request.customerId() + "|" + request.billingPeriod()
                + "|" + request.currency() + "|" + request.lineAmounts();
        return "invoice-orchestration-" + UUID.nameUUIDFromBytes(rawKey.getBytes(StandardCharsets.UTF_8));
    }
}
