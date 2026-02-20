package com.mustafabulu.billing.invoicebatchservice.api;

import com.mustafabulu.billing.invoicebatchservice.api.dto.GenerateInvoiceRequest;
import com.mustafabulu.billing.invoicebatchservice.application.InvoiceGenerationService;
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

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceGenerationService invoiceGenerationService;

    public InvoiceController(InvoiceGenerationService invoiceGenerationService) {
        this.invoiceGenerationService = invoiceGenerationService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Invoice generate(@Valid @RequestBody GenerateInvoiceRequest request) {
        return invoiceGenerationService.generate(request);
    }

    @GetMapping("/{invoiceId}")
    public Invoice getById(@PathVariable("invoiceId") String invoiceId) {
        return invoiceGenerationService.findById(invoiceId);
    }
}
