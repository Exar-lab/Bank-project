package com.banco.co.notification.email.adapter;

import com.banco.co.notification.email.dto.EmailMessage;
import com.banco.co.notification.email.port.IEmailDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "banco.mail", name = "enabled", havingValue = "false")
public class NoOpEmailDispatcher implements IEmailDispatcher {

    @Override
    public void dispatch(EmailMessage message) {
        // Intentional no-op when mail is disabled.
    }
}
