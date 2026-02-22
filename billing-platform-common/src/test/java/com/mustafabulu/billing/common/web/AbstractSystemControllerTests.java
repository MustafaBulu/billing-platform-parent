package com.mustafabulu.billing.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafabulu.billing.common.system.SystemHealthProbe;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AbstractSystemControllerTests {

    @Test
    void shouldReturnProbePayloadFromHealthEndpoint() {
        Map<String, Object> payload = Map.of("service", "test", "status", "UP");
        SystemHealthProbe probe = () -> payload;
        AbstractSystemController controller = new AbstractSystemController(probe) {
        };

        Map<String, Object> response = controller.health();

        assertThat(response).isEqualTo(payload);
    }
}
