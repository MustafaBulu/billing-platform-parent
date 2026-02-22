package com.mustafabulu.billing.billingservice.api;

import com.mustafabulu.billing.common.web.ApiErrorResponse;
import com.mustafabulu.billing.billingservice.api.dto.RateRequest;
import com.mustafabulu.billing.billingservice.api.dto.RateResponse;
import com.mustafabulu.billing.billingservice.application.BillingRatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Billing", description = "Rating and billing calculations")
public class BillingController {

    private final BillingRatingService billingRatingService;

    public BillingController(BillingRatingService billingRatingService) {
        this.billingRatingService = billingRatingService;
    }

    @PostMapping("/rate")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Rate usage",
            description = "Calculates billable total amount from usage quantity and unit price.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Rating request payload",
                    content = @Content(
                            schema = @Schema(implementation = RateRequest.class),
                            examples = @ExampleObject(
                                    name = "RateRequest",
                                    value = "{\"tenantId\":\"acme-tr\",\"customerId\":\"cust-1001\",\"metricCode\":\"api_call\",\"quantity\":1200,\"unitPrice\":0.05,\"currency\":\"USD\"}"
                            )
                    )
            )
    )
    
            @ApiResponse(
                    responseCode = "200",
                    description = "Rating calculated",
                    content = @Content(
                            schema = @Schema(implementation = RateResponse.class),
                            examples = @ExampleObject(
                                    name = "RateResponse",
                                    value = "{\"tenantId\":\"acme-tr\",\"customerId\":\"cust-1001\",\"metricCode\":\"api_call\",\"quantity\":1200,\"unitPrice\":0.05,\"totalAmount\":60.00,\"currency\":\"USD\",\"ratedAt\":\"2026-02-21T16:30:00Z\"}"
                            )
                    )
            )
    @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    
    public RateResponse rate(@Valid @RequestBody RateRequest request) {
        return billingRatingService.rate(request);
    }
}



