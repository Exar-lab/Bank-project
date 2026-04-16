package com.banco.co.notification.email.port;

import java.util.Map;

public interface IEmailTemplateRenderer {
    String render(String templateName, Map<String, Object> context);
}
