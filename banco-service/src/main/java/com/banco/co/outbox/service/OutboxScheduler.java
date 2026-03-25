package com.banco.co.outbox.service;

import com.banco.co.outbox.adapter.KafkaEventPublisher;
import com.banco.co.outbox.enums.OutboxStatus;
import com.banco.co.outbox.model.OutboxEvent;
import com.banco.co.outbox.repository.IOutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final IOutboxEventRepository outboxEventRepository;
    private final KafkaEventPublisher kafkaEventPublisher;

    public OutboxScheduler(IOutboxEventRepository outboxEventRepository,
                           KafkaEventPublisher kafkaEventPublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaEventPublisher   = kafkaEventPublisher;
    }

    @Scheduled(fixedDelay = 5000)
    public void processOutbox() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatus(OutboxStatus.PENDING);
        if (!pending.isEmpty()) {
            log.debug("Processing {} pending outbox events", pending.size());
            pending.forEach(kafkaEventPublisher::publish);
        }
    }
}
