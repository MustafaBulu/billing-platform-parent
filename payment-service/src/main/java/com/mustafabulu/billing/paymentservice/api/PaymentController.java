package com.mustafabulu.billing.paymentservice.api;

import com.mustafabulu.billing.common.web.ApiErrorResponse;
import com.mustafabulu.billing.paymentservice.api.dto.ProcessPaymentRequest;
import com.mustafabulu.billing.paymentservice.application.PaymentApplicationService;
import com.mustafabulu.billing.paymentservice.domain.PaymentResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment processing endpoints")
public class PaymentController {

    private final PaymentApplicationService paymentApplicationService;

    public PaymentController(PaymentApplicationService paymentApplicationService) {
        this.paymentApplicationService = paymentApplicationService;
    }

    @PostMapping("/process")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Process payment",
            description = "Processes payment request and returns transaction details.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Payment processing payload",
                    content = @Content(
                            schema = @Schema(implementation = ProcessPaymentRequest.class),
                            examples = @ExampleObject(
                                    name = "ProcessPayment",
                                    value = "{\"tenantId\":\"acme-tr\",\"invoiceId\":\"inv-2026-0001\",\"idempotencyKey\":\"pay-evt-0001\",\"amount\":60.00,\"currency\":\"USD\"}"
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Payment accepted for processing",
                    content = @Content(
                            schema = @Schema(implementation = PaymentResult.class),
                            examples = @ExampleObject(
                                    name = "PaymentResult",
                                    value = "{\"transactionId\":\"txn-19af2\",\"invoiceId\":\"inv-2026-0001\",\"amount\":60.00,\"currency\":\"USD\",\"status\":\"AUTHORIZED\",\"providerReference\":\"sim-gateway-8891\",\"processedAt\":\"2026-02-21T16:30:00Z\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public PaymentResult process(@Valid @RequestBody ProcessPaymentRequest request) {
        return paymentApplicationService.process(request);
    }
}
