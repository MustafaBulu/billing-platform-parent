package com.mustafabulu.billing.invoicebatchservice.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.mustafabulu.billing.invoicebatchservice.persistence.InvoiceDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.InvoiceRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvoiceE2ETests {

    @LocalServerPort
    private int port;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> System.getenv().getOrDefault("INVOICE_E2E_MONGODB_URI", "mongodb://localhost:27017/billing_invoice_e2e"));
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @AfterEach
    void cleanUp() {
        invoiceRepository.deleteAll();
    }

    @Test
    void shouldGenerateInvoiceAndGetById() {
        String payload = """
                {
                  "tenantId": "tenant-e2e",
                  "customerId": "customer-1",
                  "billingPeriod": "2026-02",
                  "currency": "USD",
                  "lineAmounts": [10.00, 2.50],
                  "idempotencyKey": "idem-e2e-1"
                }
                """;

        String invoiceId = given()
                .baseUri("http://localhost")
                .port(port)
                .header("Authorization", "Bearer dev-admin-token")
                .header("X-Tenant-Id", "tenant-e2e")
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/invoices/generate")
                .then()
                .statusCode(202)
                .body("status", equalTo("GENERATED"))
                .extract()
                .path("invoiceId");

        given()
                .baseUri("http://localhost")
                .port(port)
                .header("Authorization", "Bearer dev-admin-token")
                .header("X-Tenant-Id", "tenant-e2e")
                .when()
                .get("/api/v1/invoices/{invoiceId}", invoiceId)
                .then()
                .statusCode(200)
                .body("invoiceId", equalTo(invoiceId))
                .body("tenantId", equalTo("tenant-e2e"));

        assertThat(invoiceRepository.count()).isEqualTo(1L);
    }

    @Test
    void shouldKeepSingleInvoiceForSameIdempotencyHeaders() {
        String payload = """
                {
                  "tenantId": "tenant-from-body",
                  "customerId": "customer-2",
                  "billingPeriod": "2026-02",
                  "currency": "USD",
                  "lineAmounts": [20.00, 3.00],
                  "idempotencyKey": "idem-from-body"
                }
                """;

        String firstInvoiceId = given()
                .baseUri("http://localhost")
                .port(port)
                .header("Authorization", "Bearer dev-admin-token")
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", "tenant-from-header")
                .header("X-Idempotency-Key", "idem-from-header")
                .body(payload)
                .when()
                .post("/api/v1/invoices/generate")
                .then()
                .statusCode(202)
                .extract()
                .path("invoiceId");

        String secondInvoiceId = given()
                .baseUri("http://localhost")
                .port(port)
                .header("Authorization", "Bearer dev-admin-token")
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", "tenant-from-header")
                .header("X-Idempotency-Key", "idem-from-header")
                .body(payload)
                .when()
                .post("/api/v1/invoices/generate")
                .then()
                .statusCode(202)
                .extract()
                .path("invoiceId");

        assertThat(secondInvoiceId).isEqualTo(firstInvoiceId);
        assertThat(invoiceRepository.count()).isEqualTo(1L);

        InvoiceDocument invoice = invoiceRepository.findAll().getFirst();
        assertThat(invoice.getTenantId()).isEqualTo("tenant-from-header");
        assertThat(invoice.getIdempotencyKey()).isEqualTo("tenant-from-header:INVOICE_GENERATE:idem-from-header");
    }
}
