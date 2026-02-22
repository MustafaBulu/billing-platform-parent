package com.mustafabulu.billing.usageservice.api;

import com.mustafabulu.billing.usageservice.application.HealthProbeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "System and health endpoints")
public class SystemController {

    private final HealthProbeService healthProbeService;

    public SystemController(HealthProbeService healthProbeService) {
        this.healthProbeService = healthProbeService;
    }

    @GetMapping("/health")
    @Operation(summary = "Health probe", description = "Returns service health and basic system metadata.")
    
            @ApiResponse(
                    responseCode = "200",
                    description = "Service is healthy",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = "{\"status\":\"UP\",\"service\":\"usage-service\",\"timestamp\":\"2026-02-21T16:30:00Z\"}"
                            )
                    )
            )
    
    public Map<String, Object> health() {
        return healthProbeService.probe();
    }
}


