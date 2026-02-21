package com.mustafabulu.billing.invoicebatchservice.api;

import com.mustafabulu.billing.invoicebatchservice.api.dto.GenerateInvoiceRequest;
import com.mustafabulu.billing.invoicebatchservice.application.InvoiceGenerationService;
import com.mustafabulu.billing.invoicebatchservice.application.InvoiceOrchestrationService;
import com.mustafabulu.billing.invoicebatchservice.application.dto.InvoiceOrchestrationResult;
import com.mustafabulu.billing.invoicebatchservice.domain.Invoice;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/invoices")
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
    public Invoice generate(@Valid @RequestBody GenerateInvoiceRequest request) {
        return invoiceGenerationService.generate(request);
    }

    @GetMapping("/{invoiceId}")
    public Invoice getById(@PathVariable String invoiceId) {
        Invoice invoice = invoiceGenerationService.findById(invoiceId);
        if (invoice == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: " + invoiceId);
        }
        return invoice;
    }

    @PostMapping("/generate-and-settle")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public InvoiceOrchestrationResult generateAndSettle(@Valid @RequestBody GenerateInvoiceRequest request) {
        return invoiceOrchestrationService.generateAndSettle(request);
    }
}
