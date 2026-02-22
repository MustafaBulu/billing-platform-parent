package com.mustafabulu.billing.common.web;

import com.mustafabulu.billing.common.system.SystemHealthProbe;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;

public abstract class AbstractSystemController {

    private final SystemHealthProbe systemHealthProbe;

    protected AbstractSystemController(SystemHealthProbe systemHealthProbe) {
        this.systemHealthProbe = systemHealthProbe;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return systemHealthProbe.probe();
    }
}
