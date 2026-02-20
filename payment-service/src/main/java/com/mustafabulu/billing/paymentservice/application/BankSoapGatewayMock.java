package com.mustafabulu.billing.paymentservice.application;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BankSoapGatewayMock {

    public String authorize(BigDecimal amount, String currency, String invoiceId) {
        if (amount.compareTo(new BigDecimal("10000")) > 0) {
            return "DECLINED-" + UUID.randomUUID();
        }
        return "APPROVED-" + currency + "-" + invoiceId + "-" + UUID.randomUUID();
    }
}
