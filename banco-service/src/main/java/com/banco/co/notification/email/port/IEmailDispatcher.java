package com.banco.co.notification.email.port;

import com.banco.co.notification.email.dto.EmailMessage;

public interface IEmailDispatcher {
    void dispatch(EmailMessage message);
}
