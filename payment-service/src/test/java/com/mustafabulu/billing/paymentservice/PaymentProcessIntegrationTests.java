package com.mustafabulu.billing.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.billing.paymentservice.persistence.PaymentRecordDocument;
import com.mustafabulu.billing.paymentservice.persistence.PaymentRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class PaymentProcessIntegrationTests {

    @Container
    static final MongoDBContainer MONGO_DB_CONTAINER =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRecordRepository paymentRecordRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_DB_CONTAINER::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @AfterEach
    void cleanUp() {
        paymentRecordRepository.deleteAll();
    }

    @Test
    void shouldProcessAndPersistPayment() throws Exception {
        String payload = """
                {
                  "tenantId": "tenant-a",
                  "invoiceId": "INV-101",
                  "idempotencyKey": "idem-101",
                  "amount": 25.00,
                  "currency": "USD"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/payments/process")
                        .header("Authorization", "Bearer dev-admin-token")
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.invoiceId").value("INV-101"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String transactionId = response.get("transactionId").asText();

        assertThat(paymentRecordRepository.count()).isEqualTo(1L);
        PaymentRecordDocument persisted = paymentRecordRepository.findAll().getFirst();
        assertThat(persisted.getTransactionId()).isEqualTo(transactionId);
        assertThat(persisted.getTenantId()).isEqualTo("tenant-a");
        assertThat(persisted.getStatus()).isEqualTo("SUCCESS");
        assertThat(persisted.getProviderReference()).startsWith("APPROVED");
    }

    @Test
    void shouldBeIdempotentUsingContextHeaders() throws Exception {
        String payload = """
                {
                  "tenantId": "tenant-from-body",
                  "invoiceId": "INV-202",
                  "idempotencyKey": "idem-from-body",
                  "amount": 42.00,
                  "currency": "USD"
                }
                """;

        MvcResult first = mockMvc.perform(post("/api/v1/payments/process")
                        .header("Authorization", "Bearer dev-admin-token")
                        .contentType(APPLICATION_JSON)
                        .header("X-Tenant-Id", "tenant-from-header")
                        .header("X-Idempotency-Key", "idem-from-header")
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/payments/process")
                        .header("Authorization", "Bearer dev-admin-token")
                        .contentType(APPLICATION_JSON)
                        .header("X-Tenant-Id", "tenant-from-header")
                        .header("X-Idempotency-Key", "idem-from-header")
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondJson = objectMapper.readTree(second.getResponse().getContentAsString());

        assertThat(secondJson.get("transactionId").asText()).isEqualTo(firstJson.get("transactionId").asText());
        assertThat(paymentRecordRepository.count()).isEqualTo(1L);

        PaymentRecordDocument persisted = paymentRecordRepository.findAll().getFirst();
        assertThat(persisted.getTenantId()).isEqualTo("tenant-from-header");
        assertThat(persisted.getIdempotencyKey()).isEqualTo("tenant-from-header:PAYMENT_PROCESS:idem-from-header");
    }
}
