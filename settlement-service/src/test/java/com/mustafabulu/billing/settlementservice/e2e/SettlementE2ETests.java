package com.mustafabulu.billing.settlementservice.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.mustafabulu.billing.settlementservice.persistence.SettlementSagaDocument;
import com.mustafabulu.billing.settlementservice.persistence.SettlementSagaRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SettlementE2ETests {

    @LocalServerPort
    private int port;

    @Autowired
    private SettlementSagaRepository settlementSagaRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> System.getenv().getOrDefault("SETTLEMENT_E2E_MONGODB_URI", "mongodb://localhost:27017/billing_settlement_e2e"));
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @AfterEach
    void cleanUp() {
        settlementSagaRepository.deleteAll();
    }

    @Test
    void shouldStartSettlementAndReadBack() {
        String payload = """
                {
                  "tenantId": "tenant-e2e",
                  "invoiceId": "INV-E2E-1",
                  "paymentTransactionId": "TX-E2E-1",
                  "idempotencyKey": "idem-e2e-1",
                  "amount": 55.00,
                  "currency": "USD",
                  "paymentStatus": "SUCCESS"
                }
                """;

        String sagaId = given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/settlements/start")
                .then()
                .statusCode(202)
                .body("status", equalTo("SETTLED"))
                .extract()
                .path("sagaId");

        given()
                .baseUri("http://localhost")
                .port(port)
                .when()
                .get("/api/v1/settlements/{sagaId}", sagaId)
                .then()
                .statusCode(200)
                .body("sagaId", equalTo(sagaId))
                .body("status", equalTo("SETTLED"));

        assertThat(settlementSagaRepository.count()).isEqualTo(1L);
    }

    @Test
    void shouldBeIdempotentWithTenantAndIdempotencyHeaders() {
        String payload = """
                {
                  "tenantId": "tenant-from-body",
                  "invoiceId": "INV-E2E-2",
                  "paymentTransactionId": "TX-E2E-2",
                  "idempotencyKey": "idem-from-body",
                  "amount": 25.00,
                  "currency": "USD",
                  "paymentStatus": "SUCCESS"
                }
                """;

        String firstSagaId = given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", "tenant-from-header")
                .header("X-Idempotency-Key", "idem-from-header")
                .body(payload)
                .when()
                .post("/api/v1/settlements/start")
                .then()
                .statusCode(202)
                .extract()
                .path("sagaId");

        String secondSagaId = given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", "tenant-from-header")
                .header("X-Idempotency-Key", "idem-from-header")
                .body(payload)
                .when()
                .post("/api/v1/settlements/start")
                .then()
                .statusCode(202)
                .extract()
                .path("sagaId");

        assertThat(secondSagaId).isEqualTo(firstSagaId);
        assertThat(settlementSagaRepository.count()).isEqualTo(1L);

        SettlementSagaDocument saga = settlementSagaRepository.findAll().getFirst();
        assertThat(saga.getTenantId()).isEqualTo("tenant-from-header");
        assertThat(saga.getIdempotencyKey()).isEqualTo("tenant-from-header:SETTLEMENT_START:idem-from-header");
    }
}
