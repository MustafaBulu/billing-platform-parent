package com.mustafabulu.billing.invoicebatchservice.application;

import com.mustafabulu.billing.common.idempotency.IdempotencyKeyResolver;
import com.mustafabulu.billing.common.tenant.TenantContextHolder;
import com.mustafabulu.billing.invoicebatchservice.api.dto.GenerateInvoiceRequest;
import com.mustafabulu.billing.invoicebatchservice.domain.Invoice;
import com.mustafabulu.billing.invoicebatchservice.persistence.InvoiceDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.InvoiceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceGenerationService {

    private static final String OPERATION_CODE = "INVOICE_GENERATE";

    private final InvoiceRepository invoiceRepository;

    public InvoiceGenerationService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public Invoice generate(GenerateInvoiceRequest request) {
        String effectiveTenantId = TenantContextHolder.getTenantId().orElse(request.tenantId());
        String effectiveKey = IdempotencyKeyResolver.resolveCompositeKey(OPERATION_CODE)
                .orElseGet(() -> effectiveTenantId + ":" + OPERATION_CODE + ":" + fallbackIdempotencyKey(request));

        InvoiceDocument existing = invoiceRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                effectiveTenantId, OPERATION_CODE, effectiveKey).orElse(null);
        if (existing != null) {
            return toDomain(existing);
        }

        BigDecimal total = request.lineAmounts().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        String invoiceId = "INV-" + UUID.randomUUID();
        InvoiceDocument document = new InvoiceDocument();
        document.setInvoiceId(invoiceId);
        document.setTenantId(effectiveTenantId);
        document.setCustomerId(request.customerId());
        document.setBillingPeriod(request.billingPeriod());
        document.setTotalAmount(total);
        document.setCurrency(request.currency());
        document.setStatus("GENERATED");
        document.setCreatedAt(Instant.now());
        document.setOperationCode(OPERATION_CODE);
        document.setIdempotencyKey(effectiveKey);

        try {
            return toDomain(invoiceRepository.save(document));
        } catch (DuplicateKeyException duplicateKeyException) {
            return invoiceRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                            effectiveTenantId, OPERATION_CODE, effectiveKey)
                    .map(this::toDomain)
                    .orElseThrow(() -> duplicateKeyException);
        }
    }

    @Transactional(readOnly = true)
    public Invoice findById(String invoiceId) {
        return invoiceRepository.findByInvoiceId(invoiceId)
                .map(this::toDomain)
                .orElse(null);
    }

    private String fallbackIdempotencyKey(GenerateInvoiceRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            return request.idempotencyKey().trim();
        }

        String rawKey = request.tenantId() + "|" + request.customerId() + "|" + request.billingPeriod()
                + "|" + request.currency() + "|" + request.lineAmounts();
        return "invoice-generate-" + UUID.nameUUIDFromBytes(rawKey.getBytes(StandardCharsets.UTF_8));
    }

    private Invoice toDomain(InvoiceDocument document) {
        return new Invoice(
                document.getInvoiceId(),
                document.getTenantId(),
                document.getCustomerId(),
                document.getBillingPeriod(),
                document.getTotalAmount(),
                document.getCurrency(),
                document.getStatus(),
                document.getCreatedAt()
        );
    }
}
