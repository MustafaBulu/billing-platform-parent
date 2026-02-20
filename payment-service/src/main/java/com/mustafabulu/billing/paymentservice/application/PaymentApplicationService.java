package com.mustafabulu.billing.paymentservice.application;

import com.mustafabulu.billing.paymentservice.api.dto.ProcessPaymentRequest;
import com.mustafabulu.billing.paymentservice.domain.PaymentResult;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PaymentApplicationService {

    private final BankSoapGatewayMock bankSoapGatewayMock;

    public PaymentApplicationService(BankSoapGatewayMock bankSoapGatewayMock) {
        this.bankSoapGatewayMock = bankSoapGatewayMock;
    }

    public PaymentResult process(ProcessPaymentRequest request) {
        String providerReference = bankSoapGatewayMock.authorize(request.amount(), request.currency(), request.invoiceId());
        String status = providerReference.startsWith("APPROVED") ? "SUCCESS" : "FAILED";

        return new PaymentResult(
                UUID.randomUUID().toString(),
                request.invoiceId(),
                request.amount(),
                request.currency(),
                status,
                providerReference,
                Instant.now()
        );
    }
}
