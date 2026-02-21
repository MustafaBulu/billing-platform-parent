package com.mustafabulu.billing.invoicebatchservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafabulu.billing.invoicebatchservice.api.dto.GenerateInvoiceRequest;
import com.mustafabulu.billing.invoicebatchservice.domain.Invoice;
import com.mustafabulu.billing.invoicebatchservice.persistence.InvoiceDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.InvoiceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InvoiceGenerationServiceTests {

    private final InvoiceRepository invoiceRepository = Mockito.mock(InvoiceRepository.class);
    private final InvoiceGenerationService invoiceGenerationService = new InvoiceGenerationService(invoiceRepository);

    @Test
    void shouldReturnExistingInvoiceForSameIdempotencyKey() {
        GenerateInvoiceRequest request = new GenerateInvoiceRequest(
                "tenant-1",
                "customer-1",
                "2026-02",
                "USD",
                List.of(new BigDecimal("10.00")),
                "idem-1"
        );
        InvoiceDocument existing = new InvoiceDocument();
        existing.setInvoiceId("INV-1");
        existing.setTenantId("tenant-1");
        existing.setCustomerId("customer-1");
        existing.setBillingPeriod("2026-02");
        existing.setTotalAmount(new BigDecimal("10.00"));
        existing.setCurrency("USD");
        existing.setStatus("GENERATED");
        existing.setCreatedAt(Instant.parse("2026-02-21T00:00:00Z"));

        when(invoiceRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                "tenant-1", "INVOICE_GENERATE", "tenant-1:INVOICE_GENERATE:idem-1")).thenReturn(Optional.of(existing));

        Invoice invoice = invoiceGenerationService.generate(request);

        assertThat(invoice.invoiceId()).isEqualTo("INV-1");
        verify(invoiceRepository, never()).save(any(InvoiceDocument.class));
    }

    @Test
    void shouldCreateInvoiceWhenNoExistingRecord() {
        GenerateInvoiceRequest request = new GenerateInvoiceRequest(
                "tenant-1",
                "customer-1",
                "2026-02",
                "USD",
                List.of(new BigDecimal("10.00"), new BigDecimal("2.50")),
                "idem-2"
        );
        when(invoiceRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                "tenant-1", "INVOICE_GENERATE", "tenant-1:INVOICE_GENERATE:idem-2")).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(InvoiceDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice invoice = invoiceGenerationService.generate(request);

        assertThat(invoice.totalAmount()).isEqualByComparingTo("12.50");
        assertThat(invoice.tenantId()).isEqualTo("tenant-1");
    }
}
