package com.mustafabulu.billing.invoicebatchservice.api;

import com.mustafabulu.billing.invoicebatchservice.application.HealthProbeService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final HealthProbeService healthProbeService;

    public SystemController(HealthProbeService healthProbeService) {
        this.healthProbeService = healthProbeService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return healthProbeService.probe();
    }
}
