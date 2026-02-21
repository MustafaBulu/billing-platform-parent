package com.mustafabulu.billing.usageservice.api;

import com.mustafabulu.billing.common.web.ApiErrorResponse;
import com.mustafabulu.billing.usageservice.api.dto.UsageEventRequest;
import com.mustafabulu.billing.usageservice.application.UsageIngestionService;
import com.mustafabulu.billing.usageservice.domain.UsageEvent;
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
@RequestMapping("/api/v1/usage")
@Tag(name = "Usage Management", description = "Usage ingestion and aggregated totals")
public class UsageController {
    private final UsageIngestionService usageIngestionService;

    public UsageController(UsageIngestionService usageIngestionService) {
        this.usageIngestionService = usageIngestionService;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Ingest usage event",
            description = "Stores a metered usage event for rating and invoicing.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Usage event payload",
                    content = @Content(
                            schema = @Schema(implementation = UsageEventRequest.class),
                            examples = @ExampleObject(
                                    name = "UsageEvent",
                                    value = "{\"tenantId\":\"acme-tr\",\"customerId\":\"cust-1001\",\"idempotencyKey\":\"usage-evt-0001\",\"metricCode\":\"api_call\",\"quantity\":120,\"occurredAt\":\"2026-02-21T16:30:00Z\"}"
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Usage event accepted",
                    content = @Content(
                            schema = @Schema(implementation = UsageEvent.class),
                            examples = @ExampleObject(
                                    name = "AcceptedUsageEvent",
                                    value = "{\"tenantId\":\"acme-tr\",\"customerId\":\"cust-1001\",\"idempotencyKey\":\"usage-evt-0001\",\"metricCode\":\"api_call\",\"quantity\":120,\"occurredAt\":\"2026-02-21T16:30:00Z\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public UsageEvent ingest(@Valid @RequestBody UsageEventRequest request) {
        return usageIngestionService.ingest(request);
    }

    @GetMapping("/totals/{tenantId}/{customerId}/{metricCode}")
    @Operation(summary = "Get usage total", description = "Returns aggregated quantity for tenant/customer/metric.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Current total quantity",
                    content = @Content(schema = @Schema(type = "integer", format = "int64", example = "1200"))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid path parameters",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public long total(@Parameter(description = "Tenant identifier", example = "acme-tr")
                      @PathVariable("tenantId") String tenantId,
                      @Parameter(description = "Customer identifier", example = "cust-1001")
                      @PathVariable("customerId") String customerId,
                      @Parameter(description = "Metric code", example = "api_call")
                      @PathVariable("metricCode") String metricCode) {
        return usageIngestionService.currentTotal(tenantId, customerId, metricCode);
    }
}
