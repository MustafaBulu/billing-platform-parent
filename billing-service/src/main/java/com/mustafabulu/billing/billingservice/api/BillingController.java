package com.mustafabulu.billing.billingservice.api;

import com.mustafabulu.billing.billingservice.api.dto.RateRequest;
import com.mustafabulu.billing.billingservice.api.dto.RateResponse;
import com.mustafabulu.billing.billingservice.application.BillingRatingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingRatingService billingRatingService;

    public BillingController(BillingRatingService billingRatingService) {
        this.billingRatingService = billingRatingService;
    }

    @PostMapping("/rate")
    @ResponseStatus(HttpStatus.OK)
    public RateResponse rate(@Valid @RequestBody RateRequest request) {
        return billingRatingService.rate(request);
    }
}
