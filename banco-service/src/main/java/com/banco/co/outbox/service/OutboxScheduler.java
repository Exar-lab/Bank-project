package com.banco.co.outbox.service;

import com.banco.co.outbox.adapter.KafkaEventPublisher;
import com.banco.co.outbox.enums.OutboxStatus;
import com.banco.co.outbox.model.OutboxEvent;
import com.banco.co.outbox.repository.IOutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;

@Component
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);
    private static final int BATCH_SIZE = 100;
    private static final List<OutboxStatus> RETRYABLE_STATUSES = List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);

    private final IOutboxEventRepository outboxEventRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final String schedulerOwner;

    public OutboxScheduler(IOutboxEventRepository outboxEventRepository,
                           KafkaEventPublisher kafkaEventPublisher,
                           @Value("${spring.application.name:banco-service}") String applicationName) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaEventPublisher   = kafkaEventPublisher;
        this.schedulerOwner        = resolveOwner(applicationName);
    }

    @Scheduled(fixedDelay = 5000)
    public void processOutbox() {
        List<OutboxEvent> retryable = outboxEventRepository.findRetryable(
                RETRYABLE_STATUSES, PageRequest.of(0, BATCH_SIZE));
        if (retryable.isEmpty()) return;

        List<Long> ids = retryable.stream().map(OutboxEvent::getId).toList();
        int claimedCount = outboxEventRepository.claimForProcessing(ids, schedulerOwner);
        if (claimedCount == 0) return;

        // Re-query only the rows this instance actually claimed (TOCTOU safety).
        // Another instance may have claimed some of these ids between the SELECT and UPDATE above.
        List<OutboxEvent> claimed = outboxEventRepository.findClaimedForProcessing(ids, schedulerOwner);
        if (claimed.isEmpty()) return;

        log.debug("Processing {} outbox events", claimed.size());
        claimed.forEach(kafkaEventPublisher::publish);
    }

    private String resolveOwner(String applicationName) {
        String host = "unknown-host";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            log.warn("Unable to resolve hostname for outbox owner, using fallback", ex);
        }
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = applicationName + "@" + host + "-" + suffix;
        return owner.length() <= 100 ? owner : owner.substring(0, 100);
    }
}
