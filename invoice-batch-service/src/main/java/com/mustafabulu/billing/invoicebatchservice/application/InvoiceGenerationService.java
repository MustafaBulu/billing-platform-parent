package com.mustafabulu.billing.invoicebatchservice.application;

import com.mustafabulu.billing.invoicebatchservice.api.dto.GenerateInvoiceRequest;
import com.mustafabulu.billing.invoicebatchservice.domain.Invoice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class InvoiceGenerationService {

    private final Map<String, Invoice> invoices = new ConcurrentHashMap<>();

    public Invoice generate(GenerateInvoiceRequest request) {
        BigDecimal total = request.lineAmounts().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        String invoiceId = "INV-" + UUID.randomUUID();
        Invoice invoice = new Invoice(
                invoiceId,
                request.tenantId(),
                request.customerId(),
                request.billingPeriod(),
                total,
                request.currency(),
                "GENERATED",
                Instant.now()
        );

        invoices.put(invoiceId, invoice);
        return invoice;
    }

    public Invoice findById(String invoiceId) {
        return invoices.get(invoiceId);
    }
}
