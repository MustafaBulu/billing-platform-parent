package com.mustafabulu.billing.usageservice.api;

import com.mustafabulu.billing.common.web.AbstractSystemController;
import com.mustafabulu.billing.usageservice.application.HealthProbeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "System and health endpoints")
public class SystemController extends AbstractSystemController {

    public SystemController(HealthProbeService healthProbeService) {
        super(healthProbeService);
    }
}
