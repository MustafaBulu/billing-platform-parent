package com.mustafabulu.billing.tenantservice.api;

import com.mustafabulu.billing.common.web.ApiErrorResponse;
import com.mustafabulu.billing.tenantservice.api.dto.CreateTenantRequest;
import com.mustafabulu.billing.tenantservice.application.TenantApplicationService;
import com.mustafabulu.billing.tenantservice.domain.Tenant;
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
@RequestMapping("/api/v1/tenants")
@Tag(name = "Tenant Management", description = "Tenant lifecycle endpoints")
public class TenantController {

    private final TenantApplicationService tenantApplicationService;

    public TenantController(TenantApplicationService tenantApplicationService) {
        this.tenantApplicationService = tenantApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create tenant",
            description = "Creates a new tenant and auto-generates a unique tenant code from display name.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Tenant creation payload",
                    content = @Content(
                            schema = @Schema(implementation = CreateTenantRequest.class),
                            examples = @ExampleObject(
                                    name = "CreateTenant",
                                    value = "{\"displayName\":\"Acme Turkey\"}"
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Tenant created",
                    content = @Content(
                            schema = @Schema(implementation = Tenant.class),
                            examples = @ExampleObject(
                                    name = "TenantCreated",
                                    value = "{\"id\":\"67b8d0902c24f41f8f995900\",\"tenantCode\":\"acme-tr\",\"displayName\":\"Acme Turkey\",\"createdAt\":\"2026-02-21T16:30:00Z\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload",
                    content = @Content(
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "ValidationError",
                                    value = "{\"timestamp\":\"2026-02-21T16:30:00Z\",\"status\":400,\"error\":\"Bad Request\",\"code\":\"VALIDATION_ERROR\",\"message\":\"Request validation failed\",\"path\":\"/api/v1/tenants\",\"requestId\":\"2f3b64e9f4a54eab\",\"details\":{\"displayName\":\"must not be blank\"}}"
                            )
                    )
            )
    })
    public Tenant create(@Valid @RequestBody CreateTenantRequest request) {
        return tenantApplicationService.create(request);
    }
}
