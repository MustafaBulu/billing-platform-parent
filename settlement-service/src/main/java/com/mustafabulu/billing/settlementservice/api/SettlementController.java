package com.mustafabulu.billing.settlementservice.api;

import com.mustafabulu.billing.settlementservice.api.dto.StartSettlementRequest;
import com.mustafabulu.billing.settlementservice.application.SettlementSagaService;
import com.mustafabulu.billing.settlementservice.domain.SettlementSaga;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private final SettlementSagaService settlementSagaService;

    public SettlementController(SettlementSagaService settlementSagaService) {
        this.settlementSagaService = settlementSagaService;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SettlementSaga start(@Valid @RequestBody StartSettlementRequest request) {
        return settlementSagaService.start(request);
    }

    @GetMapping("/{sagaId}")
    public SettlementSaga getById(@PathVariable("sagaId") String sagaId) {
        SettlementSaga saga = settlementSagaService.getById(sagaId);
        if (saga == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Settlement saga not found: " + sagaId);
        }
        return saga;
    }
}
