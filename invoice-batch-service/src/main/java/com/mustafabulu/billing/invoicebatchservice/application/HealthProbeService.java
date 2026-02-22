package com.mustafabulu.billing.invoicebatchservice.application;

import com.mustafabulu.billing.common.system.SystemHealthProbe;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HealthProbeService implements SystemHealthProbe {

    public Map<String, Object> probe() {
        return Map.of(
                "service", "invoice-batch-service",
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }
}

