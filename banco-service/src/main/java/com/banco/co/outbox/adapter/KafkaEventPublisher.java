package com.banco.co.outbox.adapter;

import com.banco.co.outbox.model.OutboxEvent;
import com.banco.co.outbox.port.IOutboxEventPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ExecutionException;

@Service
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final IOutboxEventPort outboxEventPort;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                IOutboxEventPort outboxEventPort) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxEventPort = outboxEventPort;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(
                event.getKafkaTopic().getTopicName(),
                event.getAggregateId(),
                event.getPayload()
            ).get(); // bloquea hasta confirmar — lanza ExecutionException ante fallo del broker
            event.markAsPublished();
            outboxEventPort.save(event);
            log.info("Published Kafka event: type={}, aggregateId={}, topic={}",
                    event.getEventType(), event.getAggregateId(), event.getKafkaTopic().getTopicName());
        } catch (ExecutionException e) {
            event.markAsFailed();
            outboxEventPort.save(event);
            String errorMessage = (e.getCause() != null && e.getCause().getMessage() != null)
                    ? e.getCause().getMessage()
                    : (e.getMessage() != null ? e.getMessage() : "Unknown execution error");
            log.error("Failed to publish Kafka event: type={}, aggregateId={}, error={}",
                     event.getEventType(), event.getAggregateId(), errorMessage, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            event.markAsFailed();
            outboxEventPort.save(event);
            log.error("Kafka publish interrupted: type={}, aggregateId={}",
                     event.getEventType(), event.getAggregateId());
        }
    }
}
