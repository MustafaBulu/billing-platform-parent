package com.mustafabulu.billing.invoicebatchservice.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.billing.common.events.PaymentRequestedEvent;
import com.mustafabulu.billing.invoicebatchservice.api.dto.GenerateInvoiceRequest;
import com.mustafabulu.billing.invoicebatchservice.application.dto.InvoiceOrchestrationResult;
import com.mustafabulu.billing.invoicebatchservice.application.dto.PaymentProcessResponse;
import com.mustafabulu.billing.invoicebatchservice.application.dto.SettlementResponse;
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
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceOrchestrationService {
    private static final String OPERATION_CODE = "INVOICE_GENERATE_AND_SETTLE";
    private static final String INBOX_STATUS_PROCESSING = "PROCESSING";
    private static final String INBOX_STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SETTLED = "SETTLED";

    private final InvoiceGenerationService invoiceGenerationService;
    private final InboxRecordRepository inboxRecordRepository;
    private final OrchestrationRecordRepository orchestrationRecordRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public InvoiceOrchestrationService(InvoiceGenerationService invoiceGenerationService,
                                       InboxRecordRepository inboxRecordRepository,
                                       OrchestrationRecordRepository orchestrationRecordRepository,
                                       OutboxEventRepository outboxEventRepository,
                                       ObjectMapper objectMapper) {
        this.invoiceGenerationService = invoiceGenerationService;
        this.inboxRecordRepository = inboxRecordRepository;
        this.orchestrationRecordRepository = orchestrationRecordRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InvoiceOrchestrationResult generateAndSettle(GenerateInvoiceRequest request) {
        return executeGenerateAndSettle(request);
    }

    @Transactional
    public InvoiceOrchestrationResult generateAndSettle(GenerateInvoiceRequest request, String authorizationHeader) {
        return executeGenerateAndSettle(request);
    }

    private InvoiceOrchestrationResult executeGenerateAndSettle(GenerateInvoiceRequest request) {
        String tenantId = request.tenantId();
        String orchestrationIdempotencyKey = buildOrchestrationIdempotencyKey(request);
        String orchestrationId = ensureInbox(tenantId, orchestrationIdempotencyKey);
        OrchestrationRecordDocument orchestration = ensureOrchestrationRecord(
                tenantId, orchestrationIdempotencyKey, orchestrationId);

        if (orchestration.getStatus() == OrchestrationStatus.SETTLEMENT_COMPLETED
                || orchestration.getStatus() == OrchestrationStatus.COMPENSATION_REQUIRED) {
            return replayResult(orchestration, request);
        }

        Invoice invoice = ensureInvoice(orchestration, request);
        if (orchestration.getStatus() == OrchestrationStatus.RECEIVED) {
            orchestration.setStatus(OrchestrationStatus.INVOICE_GENERATED);
            orchestration.setUpdatedAt(Instant.now());
            orchestrationRecordRepository.save(orchestration);

            PaymentRequestedEvent paymentRequestedEvent = new PaymentRequestedEvent(
                    "EVT-" + UUID.randomUUID(),
                    tenantId,
                    orchestration.getOrchestrationId(),
                    orchestrationIdempotencyKey,
                    invoice.invoiceId(),
                    invoice.totalAmount(),
                    invoice.currency(),
                    Instant.now()
            );
            writeOutboxEvent(orchestration, "PAYMENT_REQUESTED", paymentRequestedEvent);
        }

        return new InvoiceOrchestrationResult(invoice, null, null);
    }

    private Invoice ensureInvoice(OrchestrationRecordDocument orchestration, GenerateInvoiceRequest request) {
        if (orchestration.getInvoiceId() != null) {
            Invoice existing = invoiceGenerationService.findById(orchestration.getInvoiceId());
            if (existing != null) {
                return existing;
            }
        }

        Invoice invoice = invoiceGenerationService.generate(request);
        orchestration.setInvoiceId(invoice.invoiceId());
        orchestration.setUpdatedAt(Instant.now());
        orchestrationRecordRepository.save(orchestration);
        return invoice;
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

    void markInboxCompleted(String tenantId, String idempotencyKey, String orchestrationId) {
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

    void markFailed(OrchestrationRecordDocument orchestration, String reason) {
        orchestration.setStatus(OrchestrationStatus.COMPENSATION_REQUIRED);
        orchestration.setFailureReason(reason);
        orchestration.setUpdatedAt(Instant.now());
        orchestrationRecordRepository.save(orchestration);
        writeOutboxEvent(orchestration, "ORCHESTRATION_FAILED", reason);
    }

    void writeOutboxEvent(OrchestrationRecordDocument orchestration, String eventType, Object payload) {
        OutboxEventDocument event = new OutboxEventDocument();
        event.setEventId("EVT-" + UUID.randomUUID());
        event.setOrchestrationId(orchestration.getOrchestrationId());
        event.setEventType(eventType);
        event.setStatus("NEW");
        event.setCreatedAt(Instant.now());
        event.setPayload(toJson(payload));
        outboxEventRepository.save(event);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return payload.toString();
        }
    }

    private InvoiceOrchestrationResult replayResult(OrchestrationRecordDocument orchestration,
                                                    GenerateInvoiceRequest request) {
        Invoice invoice = ensureInvoice(orchestration, request);
        PaymentProcessResponse payment = null;
        SettlementResponse settlement = null;

        if (orchestration.getPaymentTransactionId() != null) {
            payment = new PaymentProcessResponse(
                    orchestration.getPaymentTransactionId(),
                    invoice.invoiceId(),
                    invoice.totalAmount(),
                    invoice.currency(),
                    orchestration.getStatus() == OrchestrationStatus.COMPENSATION_REQUIRED ? STATUS_FAILED : "SUCCESS",
                    null,
                    orchestration.getUpdatedAt()
            );
        }
        if (orchestration.getSettlementSagaId() != null) {
            String settlementStatus = orchestration.getStatus() == OrchestrationStatus.SETTLEMENT_COMPLETED
                    ? STATUS_SETTLED
                    : STATUS_FAILED;
            settlement = new SettlementResponse(
                    orchestration.getSettlementSagaId(),
                    orchestration.getTenantId(),
                    invoice.invoiceId(),
                    orchestration.getPaymentTransactionId(),
                    invoice.totalAmount(),
                    invoice.currency(),
                    settlementStatus,
                    settlementStatus.equals(STATUS_SETTLED)
                            ? List.of("STARTED", "PAYMENT_CONFIRMED", STATUS_SETTLED)
                            : List.of("STARTED", STATUS_FAILED),
                    orchestration.getUpdatedAt()
            );
        }

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
