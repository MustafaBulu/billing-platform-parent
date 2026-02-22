package com.mustafabulu.billing.settlementservice.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.billing.settlementservice.api.dto.StartSettlementRequest;
import com.mustafabulu.billing.settlementservice.application.SettlementSagaService;
import com.mustafabulu.billing.settlementservice.domain.SettlementSaga;
import com.mustafabulu.billing.settlementservice.domain.SettlementStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
class SettlementControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SettlementSagaService settlementSagaService;

    @Test
    void shouldStartSettlementSaga() throws Exception {
        StartSettlementRequest request = new StartSettlementRequest(
                "tenant-1",
                "INV-1",
                "TX-1",
                "idem-1",
                new BigDecimal("50.00"),
                "USD",
                "SUCCESS"
        );
        SettlementSaga saga = new SettlementSaga(
                "SAGA-1",
                "tenant-1",
                "INV-1",
                "TX-1",
                new BigDecimal("50.00"),
                "USD",
                SettlementStatus.SETTLED,
                List.of(SettlementStatus.STARTED, SettlementStatus.PAYMENT_CONFIRMED, SettlementStatus.SETTLED),
                Instant.parse("2026-02-21T00:00:00Z")
        );
        when(settlementSagaService.start(any(StartSettlementRequest.class))).thenReturn(saga);

        mockMvc.perform(post("/api/v1/settlements/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sagaId", is("SAGA-1")))
                .andExpect(jsonPath("$.status", is("SETTLED")));
    }

    @Test
    void shouldReturnNotFoundWhenSagaMissing() throws Exception {
        when(settlementSagaService.getById("SAGA-404")).thenReturn(null);

        mockMvc.perform(get("/api/v1/settlements/SAGA-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }
}
