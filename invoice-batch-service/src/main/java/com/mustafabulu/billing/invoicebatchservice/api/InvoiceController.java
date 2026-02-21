package com.mustafabulu.billing.invoicebatchservice.api;

import com.mustafabulu.billing.common.exception.ResourceNotFoundException;
import com.mustafabulu.billing.common.web.ApiErrorResponse;
import com.mustafabulu.billing.invoicebatchservice.api.dto.GenerateInvoiceRequest;
import com.mustafabulu.billing.invoicebatchservice.application.InvoiceGenerationService;
import com.mustafabulu.billing.invoicebatchservice.application.InvoiceOrchestrationService;
import com.mustafabulu.billing.invoicebatchservice.application.dto.InvoiceOrchestrationResult;
import com.mustafabulu.billing.invoicebatchservice.domain.Invoice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoice Batch", description = "Invoice generation and orchestration endpoints")
public class InvoiceController {

    private final InvoiceGenerationService invoiceGenerationService;
    private final InvoiceOrchestrationService invoiceOrchestrationService;

    public InvoiceController(InvoiceGenerationService invoiceGenerationService,
                             InvoiceOrchestrationService invoiceOrchestrationService) {
        this.invoiceGenerationService = invoiceGenerationService;
        this.invoiceOrchestrationService = invoiceOrchestrationService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Generate invoice",
            description = "Creates an invoice for a tenant/customer/billing period.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Invoice generation payload",
                    content = @Content(
                            schema = @Schema(implementation = GenerateInvoiceRequest.class),
                            examples = @ExampleObject(
                                    name = "GenerateInvoice",
                                    value = "{\"tenantId\":\"acme-tr\",\"customerId\":\"cust-1001\",\"billingPeriod\":\"2026-02\",\"currency\":\"USD\",\"lineAmounts\":[15.0,20.0,25.0],\"idempotencyKey\":\"inv-evt-0001\"}"
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Invoice generated",
                    content = @Content(
                            schema = @Schema(implementation = Invoice.class),
                            examples = @ExampleObject(
                                    name = "InvoiceGenerated",
                                    value = "{\"invoiceId\":\"inv-2026-0001\",\"tenantId\":\"acme-tr\",\"customerId\":\"cust-1001\",\"billingPeriod\":\"2026-02\",\"totalAmount\":60.00,\"currency\":\"USD\",\"status\":\"PENDING\",\"createdAt\":\"2026-02-21T16:30:00Z\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public Invoice generate(@Valid @RequestBody GenerateInvoiceRequest request) {
        return invoiceGenerationService.generate(request);
    }

    @GetMapping("/{invoiceId}")
    @Operation(summary = "Get invoice by id", description = "Returns invoice details by invoice identifier.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Invoice found",
                    content = @Content(schema = @Schema(implementation = Invoice.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Invoice not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public Invoice getById(@PathVariable("invoiceId") String invoiceId) {
        Invoice invoice = invoiceGenerationService.findById(invoiceId);
        if (invoice == null) {
            throw new ResourceNotFoundException("Invoice not found: " + invoiceId);
        }
        return invoice;
    }

    @PostMapping("/generate-and-settle")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Generate invoice and settle",
            description = "Orchestrates invoice creation, payment processing and settlement saga.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Invoice generation payload used for orchestration",
                    content = @Content(schema = @Schema(implementation = GenerateInvoiceRequest.class))
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Invoice, payment and settlement returned",
                    content = @Content(
                            schema = @Schema(implementation = InvoiceOrchestrationResult.class),
                            examples = @ExampleObject(
                                    name = "OrchestrationResult",
                                    value = "{\"invoice\":{\"invoiceId\":\"inv-2026-0001\",\"tenantId\":\"acme-tr\",\"customerId\":\"cust-1001\",\"billingPeriod\":\"2026-02\",\"totalAmount\":60.00,\"currency\":\"USD\",\"status\":\"PENDING\",\"createdAt\":\"2026-02-21T16:30:00Z\"},\"payment\":{\"transactionId\":\"txn-19af2\",\"invoiceId\":\"inv-2026-0001\",\"amount\":60.00,\"currency\":\"USD\",\"status\":\"AUTHORIZED\",\"providerReference\":\"sim-gateway-8891\",\"processedAt\":\"2026-02-21T16:30:01Z\"},\"settlement\":{\"sagaId\":\"saga-8d11\",\"tenantId\":\"acme-tr\",\"invoiceId\":\"inv-2026-0001\",\"paymentTransactionId\":\"txn-19af2\",\"amount\":60.00,\"currency\":\"USD\",\"status\":\"SETTLED\",\"transitions\":[\"STARTED\",\"PAYMENT_CONFIRMED\",\"SETTLED\"],\"updatedAt\":\"2026-02-21T16:30:02Z\"}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public InvoiceOrchestrationResult generateAndSettle(@Valid @RequestBody GenerateInvoiceRequest request) {
        return invoiceOrchestrationService.generateAndSettle(request);
    }
}
