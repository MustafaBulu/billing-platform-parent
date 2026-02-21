package com.mustafabulu.billing.invoicebatchservice.application;

import com.mustafabulu.billing.invoicebatchservice.persistence.OutboxEventDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class OutboxPublisherJob {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);
    private static final String STATUS_NEW = "NEW";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";

    private final OutboxEventRepository outboxEventRepository;

    @Value("${platform.outbox.publisher.enabled:true}")
    private boolean enabled;

    @Value("${platform.outbox.publisher.batch-size:50}")
    private int batchSize;

    public OutboxPublisherJob(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Scheduled(fixedDelayString = "${platform.outbox.publisher.fixed-delay-ms:5000}")
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
            log.info("outbox_publish eventId={} type={} orchestrationId={} payload={}",
                    event.getEventId(), event.getEventType(), event.getOrchestrationId(), event.getPayload());

            event.setStatus(STATUS_SENT);
            event.setPublishedAt(Instant.now());
            event.setLastError(null);
            outboxEventRepository.save(event);
        } catch (Exception ex) {
            event.setStatus(STATUS_FAILED);
            event.setLastError(ex.getMessage());
            outboxEventRepository.save(event);
            log.error("outbox_publish_failed eventId={} attempt={} error={}",
                    event.getEventId(), nextAttempt, ex.getMessage(), ex);
        }
    }
}

