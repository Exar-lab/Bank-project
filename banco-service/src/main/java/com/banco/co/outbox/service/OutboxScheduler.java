package com.banco.co.outbox.service;

import com.banco.co.outbox.adapter.KafkaEventPublisher;
import com.banco.co.outbox.enums.OutboxStatus;
import com.banco.co.outbox.model.OutboxEvent;
import com.banco.co.outbox.repository.IOutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);
    private static final int BATCH_SIZE = 100;
    private static final List<OutboxStatus> RETRYABLE_STATUSES = List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);

    private final IOutboxEventRepository outboxEventRepository;
    private final KafkaEventPublisher kafkaEventPublisher;

    public OutboxScheduler(IOutboxEventRepository outboxEventRepository,
                           KafkaEventPublisher kafkaEventPublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaEventPublisher   = kafkaEventPublisher;
    }

    @Scheduled(fixedDelay = 5000)
    public void processOutbox() {
        List<OutboxEvent> retryable = outboxEventRepository.findRetryable(
                RETRYABLE_STATUSES, PageRequest.of(0, BATCH_SIZE));
        if (retryable.isEmpty()) return;

        List<Long> ids = retryable.stream().map(OutboxEvent::getId).toList();
        outboxEventRepository.claimForProcessing(ids);

        log.debug("Processing {} outbox events", retryable.size());
        retryable.forEach(kafkaEventPublisher::publish);
    }
}
