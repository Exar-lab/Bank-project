package com.banco.co.notification.email.repository;

import com.banco.co.notification.email.model.EmailOutboxEvent;
import com.banco.co.notification.email.model.EmailOutboxStatus;
import com.banco.co.notification.email.port.IEmailOutboxRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class EmailOutboxRepositoryAdapter implements IEmailOutboxRepository {

    private final EmailOutboxEventJpaRepository jpaRepository;

    public EmailOutboxRepositoryAdapter(EmailOutboxEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public EmailOutboxEvent save(EmailOutboxEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public Optional<EmailOutboxEvent> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Long> lockNextPendingIds(int limit) {
        return jpaRepository.lockNextPendingIds(limit);
    }

    @Override
    public List<EmailOutboxEvent> findClaimedForProcessing(List<Long> ids, String owner, int limit) {
        return jpaRepository.findClaimedForProcessing(ids, EmailOutboxStatus.PROCESSING, PageRequest.of(0, limit)).stream()
                .filter(event -> owner.equals(event.getClaimedBy()))
                .toList();
    }

    @Override
    public int claimForProcessing(List<Long> ids, String owner) {
        return jpaRepository.claimForProcessing(ids, owner);
    }
}
