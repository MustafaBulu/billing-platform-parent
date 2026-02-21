package com.mustafabulu.billing.usageservice.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.mustafabulu.billing.usageservice.persistence.UsageAggregateRepository;
import com.mustafabulu.billing.usageservice.persistence.UsageEventRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UsageE2ETests {

    @LocalServerPort
    private int port;

    @Autowired
    private UsageEventRepository usageEventRepository;

    @Autowired
    private UsageAggregateRepository usageAggregateRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> System.getenv().getOrDefault("USAGE_E2E_MONGODB_URI", "mongodb://localhost:27017/billing_usage_e2e"));
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @AfterEach
    void cleanUp() {
        usageEventRepository.deleteAll();
        usageAggregateRepository.deleteAll();
    }

    @Test
    void shouldIngestUsageAndReturnTotal() {
        String payload = """
                {
                  "tenantId": "tenant-e2e",
                  "customerId": "customer-1",
                  "idempotencyKey": "idem-e2e-1",
                  "metricCode": "api_call",
                  "quantity": 5,
                  "occurredAt": "2026-02-21T00:00:00Z"
                }
                """;

        given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/usage/events")
                .then()
                .statusCode(202)
                .body("tenantId", equalTo("tenant-e2e"))
                .body("quantity", equalTo(5));

        Long total = given()
                .baseUri("http://localhost")
                .port(port)
                .when()
                .get("/api/v1/usage/totals/tenant-e2e/customer-1/api_call")
                .then()
                .statusCode(200)
                .extract()
                .as(Long.class);

        assertThat(total).isEqualTo(5L);
        assertThat(usageEventRepository.count()).isEqualTo(1L);
        assertThat(usageAggregateRepository.count()).isEqualTo(1L);
    }

    @Test
    void shouldKeepSingleEventForSameIdempotencyKey() {
        String payload = """
                {
                  "tenantId": "tenant-e2e",
                  "customerId": "customer-2",
                  "idempotencyKey": "idem-e2e-dup",
                  "metricCode": "storage_gb",
                  "quantity": 7,
                  "occurredAt": "2026-02-21T00:00:00Z"
                }
                """;

        given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/usage/events")
                .then()
                .statusCode(202);

        given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/usage/events")
                .then()
                .statusCode(202);

        Long total = given()
                .baseUri("http://localhost")
                .port(port)
                .when()
                .get("/api/v1/usage/totals/tenant-e2e/customer-2/storage_gb")
                .then()
                .statusCode(200)
                .extract()
                .as(Long.class);

        assertThat(total).isEqualTo(7L);
        assertThat(usageEventRepository.count()).isEqualTo(1L);
    }
}
