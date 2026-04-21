package com.banco.co.notification.email.repository;

import com.banco.co.notification.email.model.EmailOutboxEvent;
import com.banco.co.notification.email.model.EmailOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailOutboxEventJpaRepository extends JpaRepository<EmailOutboxEvent, Long> {

    Optional<EmailOutboxEvent> findByEventId(String eventId);

    @Query(value = "SELECT * FROM email_outbox_events WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<EmailOutboxEvent> findByIdForUpdate(@Param("id") Long id);

    long countByEventId(String eventId);

    @Query(value = """
            SELECT id
              FROM email_outbox_events
             WHERE status = 'PENDING'
               AND available_at <= NOW(6)
             ORDER BY created_at ASC
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    @Transactional
    List<Long> lockNextPendingIds(@Param("limit") int limit);

    @Query("""
            SELECT e
            FROM EmailOutboxEvent e
            WHERE e.id IN :ids
              AND e.status = :status
            """)
    @Transactional(readOnly = true)
    List<EmailOutboxEvent> findClaimedForProcessing(
            @Param("ids") List<Long> ids,
            @Param("status") EmailOutboxStatus status,
            Pageable pageable
    );

    @Modifying
    @Transactional
    @Query("""
            UPDATE EmailOutboxEvent e
               SET e.status = com.banco.co.notification.email.model.EmailOutboxStatus.PROCESSING,
                   e.claimedBy = :owner
             WHERE e.id IN :ids
               AND e.status = com.banco.co.notification.email.model.EmailOutboxStatus.PENDING
            """)
    int claimForProcessing(@Param("ids") List<Long> ids, @Param("owner") String owner);
}
