package com.mustafabulu.billing.invoicebatchservice.application;

import com.mustafabulu.billing.invoicebatchservice.api.dto.GenerateInvoiceRequest;
import com.mustafabulu.billing.invoicebatchservice.application.dto.InvoiceOrchestrationResult;
import com.mustafabulu.billing.invoicebatchservice.application.dto.PaymentProcessRequest;
import com.mustafabulu.billing.invoicebatchservice.application.dto.PaymentProcessResponse;
import com.mustafabulu.billing.invoicebatchservice.application.dto.SettlementResponse;
import com.mustafabulu.billing.invoicebatchservice.application.dto.StartSettlementRequest;
import com.mustafabulu.billing.invoicebatchservice.domain.Invoice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class InvoiceOrchestrationService {

    private final InvoiceGenerationService invoiceGenerationService;
    private final RestClient paymentRestClient;
    private final RestClient settlementRestClient;

    public InvoiceOrchestrationService(InvoiceGenerationService invoiceGenerationService,
                                       @Value("${integration.payment.base-url}") String paymentBaseUrl,
                                       @Value("${integration.settlement.base-url}") String settlementBaseUrl) {
        this.invoiceGenerationService = invoiceGenerationService;
        this.paymentRestClient = RestClient.builder().baseUrl(paymentBaseUrl).build();
        this.settlementRestClient = RestClient.builder().baseUrl(settlementBaseUrl).build();
    }

    public InvoiceOrchestrationResult generateAndSettle(GenerateInvoiceRequest request) {
        Invoice invoice = invoiceGenerationService.generate(request);

        PaymentProcessResponse paymentResponse = paymentRestClient.post()
                .uri("/api/v1/payments/process")
                .body(new PaymentProcessRequest(
                        request.tenantId(),
                        invoice.invoiceId(),
                        invoice.totalAmount(),
                        invoice.currency()))
                .retrieve()
                .body(PaymentProcessResponse.class);

        SettlementResponse settlementResponse = settlementRestClient.post()
                .uri("/api/v1/settlements/start")
                .body(new StartSettlementRequest(
                        request.tenantId(),
                        invoice.invoiceId(),
                        paymentResponse.transactionId(),
                        invoice.totalAmount(),
                        invoice.currency(),
                        paymentResponse.status()))
                .retrieve()
                .body(SettlementResponse.class);

        return new InvoiceOrchestrationResult(invoice, paymentResponse, settlementResponse);
    }
}
