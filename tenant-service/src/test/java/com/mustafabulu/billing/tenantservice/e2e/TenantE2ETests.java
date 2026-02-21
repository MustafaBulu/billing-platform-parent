package com.mustafabulu.billing.tenantservice.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.mustafabulu.billing.tenantservice.persistence.TenantDocument;
import com.mustafabulu.billing.tenantservice.persistence.TenantRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantE2ETests {

    @LocalServerPort
    private int port;

    @Autowired
    private TenantRepository tenantRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> System.getenv().getOrDefault("TENANT_E2E_MONGODB_URI", "mongodb://localhost:27017/billing_tenant_e2e"));
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @AfterEach
    void cleanUp() {
        tenantRepository.deleteAll();
    }

    @Test
    void shouldCreateTenantAndPersist() {
        String payload = """
                {
                  "tenantCode": "tenant-e2e",
                  "displayName": "Tenant E2E"
                }
                """;

        String id = given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/tenants")
                .then()
                .statusCode(201)
                .body("tenantCode", equalTo("tenant-e2e"))
                .extract()
                .path("id");

        assertThat(id).isNotBlank();
        assertThat(tenantRepository.count()).isEqualTo(1L);
    }

    @Test
    void shouldReturnExistingTenantForSameTenantCode() {
        String firstPayload = """
                {
                  "tenantCode": "tenant-dup",
                  "displayName": "Tenant First"
                }
                """;
        String secondPayload = """
                {
                  "tenantCode": "tenant-dup",
                  "displayName": "Tenant Second"
                }
                """;

        String firstId = given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .body(firstPayload)
                .when()
                .post("/api/v1/tenants")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String secondId = given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON)
                .body(secondPayload)
                .when()
                .post("/api/v1/tenants")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        assertThat(secondId).isEqualTo(firstId);
        assertThat(tenantRepository.count()).isEqualTo(1L);

        TenantDocument tenant = tenantRepository.findAll().getFirst();
        assertThat(tenant.getDisplayName()).isEqualTo("Tenant First");
    }
}
