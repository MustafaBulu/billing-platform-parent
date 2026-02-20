package com.mustafabulu.billing.paymentservice.application;

import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HealthProbeService {

    public Map<String, Object> probe() {
        return Map.of(
                "service", "payment-service",
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }
}
