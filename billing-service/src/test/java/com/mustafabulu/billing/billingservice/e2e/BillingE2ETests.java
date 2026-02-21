package com.mustafabulu.billing.billingservice.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.ContentType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BillingE2ETests {

    @LocalServerPort
    private int port;

    @Test
    void shouldRateUsageSuccessfully() {
        String payload = """
                {
                  "tenantId": "tenant-e2e",
                  "customerId": "customer-1",
                  "metricCode": "api_call",
                  "quantity": 25,
                  "unitPrice": 0.105,
                  "currency": "USD"
                }
                """;

        BigDecimal totalAmount = given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/billing/rate")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getObject("totalAmount", BigDecimal.class);

        assertThat(totalAmount).isEqualByComparingTo("2.63");
    }

    @Test
    void shouldReturnZeroTotalWhenQuantityIsZero() {
        String payload = """
                {
                  "tenantId": "tenant-e2e",
                  "customerId": "customer-2",
                  "metricCode": "api_call",
                  "quantity": 0,
                  "unitPrice": 0.105,
                  "currency": "USD"
                }
                """;

        BigDecimal totalAmount = given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/billing/rate")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getObject("totalAmount", BigDecimal.class);

        assertThat(totalAmount).isEqualByComparingTo("0.00");
    }
}
