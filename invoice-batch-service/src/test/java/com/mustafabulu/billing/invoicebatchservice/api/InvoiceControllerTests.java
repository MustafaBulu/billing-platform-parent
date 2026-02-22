package com.mustafabulu.billing.invoicebatchservice.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.billing.invoicebatchservice.api.dto.GenerateInvoiceRequest;
import com.mustafabulu.billing.invoicebatchservice.application.InvoiceGenerationService;
import com.mustafabulu.billing.invoicebatchservice.application.InvoiceOrchestrationService;
import com.mustafabulu.billing.invoicebatchservice.application.dto.InvoiceOrchestrationResult;
import com.mustafabulu.billing.invoicebatchservice.application.dto.PaymentProcessResponse;
import com.mustafabulu.billing.invoicebatchservice.application.dto.SettlementResponse;
import com.mustafabulu.billing.invoicebatchservice.domain.Invoice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvoiceController.class)
@AutoConfigureMockMvc(addFilters = false)
class InvoiceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InvoiceGenerationService invoiceGenerationService;

    @MockBean
    private InvoiceOrchestrationService invoiceOrchestrationService;

    @Test
    void shouldGenerateInvoice() throws Exception {
        GenerateInvoiceRequest request = new GenerateInvoiceRequest(
                "tenant-1",
                "customer-1",
                "2026-02",
                "USD",
                List.of(new BigDecimal("10.00"), new BigDecimal("5.00")),
                "idem-1"
        );
        Invoice invoice = new Invoice(
                "INV-1",
                "tenant-1",
                "customer-1",
                "2026-02",
                new BigDecimal("15.00"),
                "USD",
                "GENERATED",
                Instant.parse("2026-02-21T00:00:00Z")
        );
        when(invoiceGenerationService.generate(any(GenerateInvoiceRequest.class))).thenReturn(invoice);

        mockMvc.perform(post("/api/v1/invoices/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.invoiceId", is("INV-1")))
                .andExpect(jsonPath("$.totalAmount", is(15.00)));
    }

    @Test
    void shouldReturnInvoiceWhenFound() throws Exception {
        Invoice invoice = new Invoice(
                "INV-2",
                "tenant-1",
                "customer-2",
                "2026-02",
                new BigDecimal("20.00"),
                "USD",
                "GENERATED",
                Instant.parse("2026-02-21T00:00:00Z")
        );
        when(invoiceGenerationService.findById("INV-2")).thenReturn(invoice);

        mockMvc.perform(get("/api/v1/invoices/INV-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId", is("INV-2")))
                .andExpect(jsonPath("$.currency", is("USD")));
    }

    @Test
    void shouldReturnNotFoundWhenInvoiceMissing() throws Exception {
        when(invoiceGenerationService.findById("INV-404")).thenReturn(null);

        mockMvc.perform(get("/api/v1/invoices/INV-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    void shouldGenerateAndSettleInvoice() throws Exception {
        GenerateInvoiceRequest request = new GenerateInvoiceRequest(
                "tenant-1",
                "customer-1",
                "2026-02",
                "USD",
                List.of(new BigDecimal("12.50")),
                "idem-2"
        );
        Invoice invoice = new Invoice(
                "INV-3",
                "tenant-1",
                "customer-1",
                "2026-02",
                new BigDecimal("12.50"),
                "USD",
                "GENERATED",
                Instant.parse("2026-02-21T00:00:00Z")
        );
        PaymentProcessResponse payment = new PaymentProcessResponse(
                "TX-1",
                "INV-3",
                new BigDecimal("12.50"),
                "USD",
                "SUCCESS",
                "APPROVED-ref",
                Instant.parse("2026-02-21T00:00:01Z")
        );
        SettlementResponse settlement = new SettlementResponse(
                "SAGA-1",
                "tenant-1",
                "INV-3",
                "TX-1",
                new BigDecimal("12.50"),
                "USD",
                "SETTLED",
                List.of("STARTED", "PAYMENT_CONFIRMED", "SETTLED"),
                Instant.parse("2026-02-21T00:00:02Z")
        );
        when(invoiceOrchestrationService.generateAndSettle(any(GenerateInvoiceRequest.class), any()))
                .thenReturn(new InvoiceOrchestrationResult(invoice, payment, settlement));

        mockMvc.perform(post("/api/v1/invoices/generate-and-settle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.invoice.invoiceId", is("INV-3")))
                .andExpect(jsonPath("$.payment.transactionId", is("TX-1")))
                .andExpect(jsonPath("$.settlement.status", is("SETTLED")));

        verify(invoiceOrchestrationService).generateAndSettle(any(GenerateInvoiceRequest.class), any());
    }
}
