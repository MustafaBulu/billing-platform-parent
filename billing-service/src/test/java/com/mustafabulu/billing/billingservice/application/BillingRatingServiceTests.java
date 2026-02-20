package com.mustafabulu.billing.billingservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafabulu.billing.billingservice.api.dto.RateRequest;
import com.mustafabulu.billing.billingservice.api.dto.RateResponse;
import com.mustafabulu.billing.common.exception.DomainValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BillingRatingServiceTests {

    private final BillingRatingService billingRatingService = new BillingRatingService();

    @Test
    void shouldRateSuccessfully() {
        RateRequest request = new RateRequest("tenant-1", "customer-1", "api_call", 25, new BigDecimal("0.105"), "USD");

        RateResponse response = billingRatingService.rate(request);

        assertThat(response.totalAmount()).isEqualByComparingTo("2.63");
        assertThat(response.unitPrice()).isEqualByComparingTo("0.1050");
    }

    @Test
    void shouldReturnZeroTotalWhenQuantityIsZero() {
        RateRequest request = new RateRequest("tenant-1", "customer-1", "api_call", 0, new BigDecimal("0.105"), "USD");

        RateResponse response = billingRatingService.rate(request);

        assertThat(response.totalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldRejectNegativeUnitPrice() {
        RateRequest request = new RateRequest("tenant-1", "customer-1", "api_call", 10, new BigDecimal("-0.1"), "USD");

        assertThatThrownBy(() -> billingRatingService.rate(request))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("unitPrice");
    }
}
