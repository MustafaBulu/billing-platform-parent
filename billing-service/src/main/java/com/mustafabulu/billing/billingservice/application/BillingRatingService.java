package com.mustafabulu.billing.billingservice.application;

import com.mustafabulu.billing.billingservice.api.dto.RateRequest;
import com.mustafabulu.billing.billingservice.api.dto.RateResponse;
import com.mustafabulu.billing.common.exception.DomainValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class BillingRatingService {

    public RateResponse rate(RateRequest request) {
        if (request.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainValidationException("unitPrice must be greater than or equal to zero");
        }

        BigDecimal quantity = BigDecimal.valueOf(request.quantity());
        BigDecimal total = request.unitPrice()
                .multiply(quantity)
                .setScale(2, RoundingMode.HALF_UP);

        return new RateResponse(
                request.tenantId(),
                request.customerId(),
                request.metricCode(),
                request.quantity(),
                request.unitPrice().setScale(4, RoundingMode.HALF_UP),
                total,
                request.currency(),
                Instant.now()
        );
    }
}
