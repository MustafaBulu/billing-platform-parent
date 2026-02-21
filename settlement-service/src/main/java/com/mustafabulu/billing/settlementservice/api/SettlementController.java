package com.mustafabulu.billing.settlementservice.api;

import com.mustafabulu.billing.common.exception.ResourceNotFoundException;
import com.mustafabulu.billing.common.web.ApiErrorResponse;
import com.mustafabulu.billing.settlementservice.api.dto.StartSettlementRequest;
import com.mustafabulu.billing.settlementservice.application.SettlementSagaService;
import com.mustafabulu.billing.settlementservice.domain.SettlementSaga;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/settlements")
@Tag(name = "Settlement", description = "Settlement saga lifecycle endpoints")
public class SettlementController {

    private final SettlementSagaService settlementSagaService;

    public SettlementController(SettlementSagaService settlementSagaService) {
        this.settlementSagaService = settlementSagaService;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Start settlement saga",
            description = "Starts settlement workflow using payment transaction context.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Settlement start payload",
                    content = @Content(
                            schema = @Schema(implementation = StartSettlementRequest.class),
                            examples = @ExampleObject(
                                    name = "StartSettlement",
                                    value = "{\"tenantId\":\"acme-tr\",\"invoiceId\":\"inv-2026-0001\",\"paymentTransactionId\":\"txn-19af2\",\"idempotencyKey\":\"set-evt-0001\",\"amount\":60.00,\"currency\":\"USD\",\"paymentStatus\":\"AUTHORIZED\"}"
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Settlement saga started",
                    content = @Content(
                            schema = @Schema(implementation = SettlementSaga.class),
                            examples = @ExampleObject(
                                    name = "SagaStarted",
                                    value = "{\"sagaId\":\"saga-8d11\",\"tenantId\":\"acme-tr\",\"invoiceId\":\"inv-2026-0001\",\"paymentTransactionId\":\"txn-19af2\",\"amount\":60.00,\"currency\":\"USD\",\"status\":\"STARTED\",\"transitions\":[\"STARTED\"],\"updatedAt\":\"2026-02-21T16:30:00Z\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public SettlementSaga start(@Valid @RequestBody StartSettlementRequest request) {
        return settlementSagaService.start(request);
    }

    @GetMapping("/{sagaId}")
    @Operation(summary = "Get settlement saga by id", description = "Returns current status and transitions of a settlement saga.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Settlement saga found",
                    content = @Content(schema = @Schema(implementation = SettlementSaga.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Settlement saga not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public SettlementSaga getById(@Parameter(description = "Settlement saga identifier", example = "saga-8d11")
                                  @PathVariable("sagaId") String sagaId) {
        SettlementSaga saga = settlementSagaService.getById(sagaId);
        if (saga == null) {
            throw new ResourceNotFoundException("Settlement saga not found: " + sagaId);
        }
        return saga;
    }
}
