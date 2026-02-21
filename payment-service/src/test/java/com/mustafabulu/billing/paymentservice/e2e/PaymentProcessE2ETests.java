package com.mustafabulu.billing.paymentservice.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.mustafabulu.billing.paymentservice.persistence.PaymentRecordDocument;
import com.mustafabulu.billing.paymentservice.persistence.PaymentRecordRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentProcessE2ETests {

    @LocalServerPort
    private int port;

    @Autowired
    private PaymentRecordRepository paymentRecordRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> System.getenv().getOrDefault("PAYMENT_E2E_MONGODB_URI", "mongodb://localhost:27017/billing_payment_e2e"));
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @AfterEach
    void cleanUp() {
        paymentRecordRepository.deleteAll();
    }

    @Test
    void shouldProcessPaymentOverHttp() {
        String payload = """
                {
                  "tenantId": "tenant-e2e",
                  "invoiceId": "INV-E2E-1",
                  "idempotencyKey": "idem-e2e-1",
                  "amount": 35.00,
                  "currency": "USD"
                }
                """;

        String transactionId = given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/payments/process")
                .then()
                .statusCode(202)
                .body("status", equalTo("SUCCESS"))
                .extract()
                .path("transactionId");

        assertThat(transactionId).isNotBlank();
        assertThat(paymentRecordRepository.count()).isEqualTo(1L);
    }

    @Test
    void shouldKeepSingleRecordForSameIdempotencyHeaders() {
        String payload = """
                {
                  "tenantId": "tenant-from-body",
                  "invoiceId": "INV-E2E-2",
                  "idempotencyKey": "idem-from-body",
                  "amount": 42.00,
                  "currency": "USD"
                }
                """;

        String firstTransactionId = given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", "tenant-from-header")
                .header("X-Idempotency-Key", "idem-from-header")
                .body(payload)
                .when()
                .post("/api/v1/payments/process")
                .then()
                .statusCode(202)
                .body("status", equalTo("SUCCESS"))
                .extract()
                .path("transactionId");

        String secondTransactionId = given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", "tenant-from-header")
                .header("X-Idempotency-Key", "idem-from-header")
                .body(payload)
                .when()
                .post("/api/v1/payments/process")
                .then()
                .statusCode(202)
                .body("status", equalTo("SUCCESS"))
                .extract()
                .path("transactionId");

        assertThat(secondTransactionId).isEqualTo(firstTransactionId);
        assertThat(paymentRecordRepository.count()).isEqualTo(1L);

        PaymentRecordDocument persisted = paymentRecordRepository.findAll().getFirst();
        assertThat(persisted.getTenantId()).isEqualTo("tenant-from-header");
        assertThat(persisted.getIdempotencyKey()).isEqualTo("tenant-from-header:PAYMENT_PROCESS:idem-from-header");
    }
}
