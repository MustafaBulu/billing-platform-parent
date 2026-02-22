package com.mustafabulu.billing.usageservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HealthProbeServiceTests {

    private final HealthProbeService healthProbeService = new HealthProbeService();

    @Test
    void shouldReturnUpStatusPayload() {
        Map<String, Object> payload = healthProbeService.probe();

        assertThat(payload.get("service")).isEqualTo("usage-service");
        assertThat(payload.get("status")).isEqualTo("UP");
        assertThat(payload.get("timestamp")).isInstanceOf(String.class);
    }
}
