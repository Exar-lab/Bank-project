package com.banco.co.notification.email.relay;

import com.banco.co.notification.email.config.MailProperties;
import com.banco.co.notification.email.model.EmailOutboxEvent;
import com.banco.co.notification.email.port.IEmailOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;

@Component
public class EmailOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxRelay.class);

    private final IEmailOutboxRepository emailOutboxRepository;
    private final ThreadPoolTaskExecutor emailDispatcherExecutor;
    private final MailProperties mailProperties;
    private final EmailOutboxDispatchWorker dispatchWorker;
    private final String relayOwner;

    public EmailOutboxRelay(
            IEmailOutboxRepository emailOutboxRepository,
            @Qualifier("emailDispatcherExecutor") ThreadPoolTaskExecutor emailDispatcherExecutor,
            MailProperties mailProperties,
            EmailOutboxDispatchWorker dispatchWorker,
            @Value("${spring.application.name:banco-service}") String applicationName
    ) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.emailDispatcherExecutor = emailDispatcherExecutor;
        this.mailProperties = mailProperties;
        this.dispatchWorker = dispatchWorker;
        this.relayOwner = resolveOwner(applicationName);
    }

    @Scheduled(fixedDelayString = "${banco.mail.relay.poll-delay-ms:10000}")
    @Transactional
    public void relayPendingEmails() {
        List<Long> ids = emailOutboxRepository.lockNextPendingIds(mailProperties.relay().batchSize());
        if (ids.isEmpty()) {
            return;
        }

        int claimed = emailOutboxRepository.claimForProcessing(ids, relayOwner);
        if (claimed == 0) {
            return;
        }

        List<EmailOutboxEvent> claimedEvents = emailOutboxRepository.findClaimedForProcessing(
                ids,
                relayOwner,
                mailProperties.relay().batchSize()
        );
        claimedEvents.forEach(event -> emailDispatcherExecutor.execute(() -> dispatchWorker.dispatchSafely(event.getId())));
    }

    private String resolveOwner(String applicationName) {
        String host = "unknown-host";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            log.warn("Unable to resolve hostname for email relay owner", ex);
        }
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = applicationName + "@" + host + "-" + suffix;
        return owner.length() <= 100 ? owner : owner.substring(0, 100);
    }
}
