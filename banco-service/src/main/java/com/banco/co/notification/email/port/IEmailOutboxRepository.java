package com.banco.co.notification.email.port;

import com.banco.co.notification.email.model.EmailOutboxEvent;

import java.util.List;
import java.util.Optional;

public interface IEmailOutboxRepository {
    EmailOutboxEvent save(EmailOutboxEvent event);

    Optional<EmailOutboxEvent> findById(Long id);

    Optional<EmailOutboxEvent> findByIdForUpdate(Long id);

    List<Long> lockNextPendingIds(int limit);

    List<EmailOutboxEvent> findClaimedForProcessing(List<Long> ids, String owner, int limit);

    int claimForProcessing(List<Long> ids, String owner);

    int markSentIfStillProcessing(Long id);
}
