package com.mustafabulu.billing.invoicebatchservice.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafabulu.billing.common.events.KafkaTopics;
import com.mustafabulu.billing.common.events.PaymentRequestedEvent;
import com.mustafabulu.billing.common.events.SettlementRequestedEvent;
import com.mustafabulu.billing.invoicebatchservice.persistence.OutboxEventDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisherJob {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);
    private static final String STATUS_NEW = "NEW";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${platform.outbox.publisher.enabled:true}")
    private boolean enabled;

    @Value("${platform.outbox.publisher.batch-size:50}")
    private int batchSize;

    public OutboxPublisherJob(OutboxEventRepository outboxEventRepository,
                              KafkaTemplate<String, Object> kafkaTemplate,
                              ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${platform.outbox.publisher.fixed-delay-ms:5000}")
    @Transactional
    public void publishPendingOutboxEvents() {
        if (!enabled) {
            return;
        }

        List<OutboxEventDocument> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                STATUS_NEW, PageRequest.of(0, Math.max(batchSize, 1)));
        for (OutboxEventDocument event : pendingEvents) {
            publishSingleEvent(event);
        }
    }

    private void publishSingleEvent(OutboxEventDocument event) {
        int nextAttempt = event.getAttemptCount() + 1;
        event.setAttemptCount(nextAttempt);
        try {
            String topic = resolveTopic(event.getEventType());
            Object payload = resolvePayload(event);
            kafkaTemplate.send(topic, event.getOrchestrationId(), payload);

            log.info("outbox_publish eventId={} type={} orchestrationId={} payload={}",
                    event.getEventId(), event.getEventType(), event.getOrchestrationId(), event.getPayload());

            event.setStatus(STATUS_SENT);
            event.setPublishedAt(Instant.now());
            event.setLastError(null);
            outboxEventRepository.save(event);
        } catch (JsonProcessingException | RuntimeException ex) {
            event.setStatus(STATUS_FAILED);
            event.setLastError(ex.getMessage());
            outboxEventRepository.save(event);
            log.error("outbox_publish_failed eventId={} attempt={} error={}",
                    event.getEventId(), nextAttempt, ex.getMessage(), ex);
        }
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "PAYMENT_REQUESTED" -> KafkaTopics.PAYMENT_REQUESTED;
            case "SETTLEMENT_REQUESTED" -> KafkaTopics.SETTLEMENT_REQUESTED;
            case "ORCHESTRATION_FAILED" -> KafkaTopics.ORCHESTRATION_FAILED;
            default -> throw new IllegalArgumentException("Unsupported outbox event type: " + eventType);
        };
    }

    private Object resolvePayload(OutboxEventDocument event) throws JsonProcessingException {
        return switch (event.getEventType()) {
            case "PAYMENT_REQUESTED" -> objectMapper.readValue(event.getPayload(), PaymentRequestedEvent.class);
            case "SETTLEMENT_REQUESTED" -> objectMapper.readValue(event.getPayload(), SettlementRequestedEvent.class);
            case "ORCHESTRATION_FAILED" -> event.getPayload();
            default -> throw new IllegalArgumentException("Unsupported outbox payload type: " + event.getEventType());
        };
    }
}
