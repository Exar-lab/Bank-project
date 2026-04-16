package com.banco.co.notification.email.adapter;

import com.banco.co.notification.email.exception.EmailSerializationException;
import com.banco.co.notification.email.port.IEmailTemplateRenderer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateEngineException;

import java.util.Locale;
import java.util.Map;

@Component
public class ThymeleafEmailTemplateRenderer implements IEmailTemplateRenderer {

    private final TemplateEngine emailTemplateEngine;

    public ThymeleafEmailTemplateRenderer(@Qualifier("emailTemplateEngine") TemplateEngine emailTemplateEngine) {
        this.emailTemplateEngine = emailTemplateEngine;
    }

    @Override
    public String render(String templateName, Map<String, Object> context) {
        try {
            Context thymeleafContext = new Context(Locale.forLanguageTag("es-CO"));
            thymeleafContext.setVariables(context);
            return emailTemplateEngine.process(templateName, thymeleafContext);
        } catch (TemplateEngineException ex) {
            throw new EmailSerializationException(templateName, ex);
        }
    }
}
