package com.mustafabulu.billing.usageservice.api;

import com.mustafabulu.billing.usageservice.api.dto.UsageEventRequest;
import com.mustafabulu.billing.usageservice.application.UsageIngestionService;
import com.mustafabulu.billing.usageservice.domain.UsageEvent;
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
public class UsageController {
    private final UsageIngestionService usageIngestionService;

    public UsageController(UsageIngestionService usageIngestionService) {
        this.usageIngestionService = usageIngestionService;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public UsageEvent ingest(@Valid @RequestBody UsageEventRequest request) {
        return usageIngestionService.ingest(request);
    }

    @GetMapping("/totals/{tenantId}/{customerId}/{metricCode}")
    public long total(@PathVariable("tenantId") String tenantId,
                      @PathVariable("customerId") String customerId,
                      @PathVariable("metricCode") String metricCode) {
        return usageIngestionService.currentTotal(tenantId, customerId, metricCode);
    }
}
