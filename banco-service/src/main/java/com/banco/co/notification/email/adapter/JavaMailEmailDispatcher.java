package com.banco.co.notification.email.adapter;

import com.banco.co.notification.email.config.MailProperties;
import com.banco.co.notification.email.dto.EmailMessage;
import com.banco.co.notification.email.exception.EmailDeliveryException;
import com.banco.co.notification.email.port.IEmailDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "banco.mail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JavaMailEmailDispatcher implements IEmailDispatcher {

    private final JavaMailSender javaMailSender;
    private final MailProperties mailProperties;

    public JavaMailEmailDispatcher(JavaMailSender javaMailSender, MailProperties mailProperties) {
        this.javaMailSender = javaMailSender;
        this.mailProperties = mailProperties;
    }

    @Override
    public void dispatch(EmailMessage message) {
        try {
            var mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(mailProperties.from());
            helper.setTo(message.recipientEmail());
            helper.setSubject(message.subject());
            helper.setText(message.htmlBody(), true);
            javaMailSender.send(mimeMessage);
        } catch (MailException | jakarta.mail.MessagingException ex) {
            throw new EmailDeliveryException(message.eventId(), ex);
        }
    }
}
