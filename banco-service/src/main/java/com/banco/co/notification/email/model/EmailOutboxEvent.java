package com.banco.co.notification.email.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_outbox_events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class EmailOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100, unique = true)
    private String eventId;

    @Column(name = "recipient_email", nullable = false, length = 254)
    private String recipientEmail;

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "recipient_name", nullable = false, length = 200)
    private String recipientName;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Column(name = "template_context_json", nullable = false, columnDefinition = "TEXT")
    private String templateContextJson;

    @Column(nullable = false, length = 998)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "claimed_by", length = 100)
    private String claimedBy;

    public EmailOutboxEvent(
            String eventId,
            UUID userId,
            String recipientEmail,
            String recipientName,
            String templateName,
            String templateContextJson,
            String subject
    ) {
        this.eventId = eventId;
        this.userId = userId;
        this.recipientEmail = recipientEmail;
        this.recipientName = recipientName;
        this.templateName = templateName;
        this.templateContextJson = templateContextJson;
        this.subject = subject;
    }

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = EmailOutboxStatus.PENDING;
        }
        if (availableAt == null) {
            availableAt = LocalDateTime.now();
        }
    }
}
