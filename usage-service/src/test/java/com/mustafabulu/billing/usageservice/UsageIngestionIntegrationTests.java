package com.mustafabulu.billing.usageservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafabulu.billing.usageservice.api.dto.UsageEventRequest;
import com.mustafabulu.billing.usageservice.application.UsageIngestionService;
import com.mustafabulu.billing.usageservice.persistence.UsageAggregateRepository;
import com.mustafabulu.billing.usageservice.persistence.UsageEventRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class UsageIngestionIntegrationTests {

    @Container
    static final MongoDBContainer MONGO_DB_CONTAINER =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Autowired
    private UsageIngestionService usageIngestionService;

    @Autowired
    private UsageEventRepository usageEventRepository;

    @Autowired
    private UsageAggregateRepository usageAggregateRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_DB_CONTAINER::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @AfterEach
    void cleanUp() {
        usageEventRepository.deleteAll();
        usageAggregateRepository.deleteAll();
    }

    @Test
    void shouldKeepSingleEventForSameIdempotencyKey() {
        UsageEventRequest request = new UsageEventRequest(
                "tenant-a",
                "customer-a",
                "idem-1",
                "api_call",
                5L,
                Instant.parse("2026-02-21T00:00:00Z")
        );

        usageIngestionService.ingest(request);
        usageIngestionService.ingest(request);

        long total = usageIngestionService.currentTotal("tenant-a", "customer-a", "api_call");

        assertThat(usageEventRepository.count()).isEqualTo(1L);
        assertThat(total).isEqualTo(5L);
    }

    @Test
    void shouldAggregateCorrectlyUnderConcurrentIngestion() throws Exception {
        int eventCount = 40;
        ExecutorService executorService = Executors.newFixedThreadPool(8);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < eventCount; i++) {
            final int index = i;
            tasks.add(() -> {
                UsageEventRequest request = new UsageEventRequest(
                        "tenant-b",
                        "customer-b",
                        "idem-" + index,
                        "storage_gb",
                        1L,
                        Instant.now()
                );
                usageIngestionService.ingest(request);
                return null;
            });
        }

        List<Future<Void>> futures = executorService.invokeAll(tasks);
        for (Future<Void> future : futures) {
            future.get();
        }
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        long total = usageIngestionService.currentTotal("tenant-b", "customer-b", "storage_gb");

        assertThat(usageEventRepository.count()).isEqualTo(eventCount);
        assertThat(total).isEqualTo(eventCount);
    }
}
