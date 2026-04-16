package com.banco.co.notification.email.adapter;

import com.banco.co.notification.email.config.MailProperties;
import com.banco.co.notification.email.config.ThymeleafEmailConfig;
import com.banco.co.notification.email.dto.EmailMessage;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JavaMailEmailDispatcherGreenMailTest {

    private GreenMail greenMail;

    @BeforeEach
    void setUp() {
        greenMail = new GreenMail(ServerSetupTest.SMTP);
        greenMail.start();
    }

    @AfterEach
    void tearDown() {
        greenMail.stop();
    }

    @Test
    void testDispatch_WhenHtmlBodyRendered_ThenGreenMailReceivesEscapedContent() throws Exception {
        ThymeleafEmailConfig thymeleafConfig = new ThymeleafEmailConfig();
        var resolver = thymeleafConfig.emailTemplateResolver(new org.springframework.core.env.StandardEnvironment());
        var templateEngine = thymeleafConfig.emailTemplateEngine(resolver);
        ThymeleafEmailTemplateRenderer renderer = new ThymeleafEmailTemplateRenderer(templateEngine);

        String htmlBody = renderer.render("email/welcome", Map.of(
                "recipientName", "<script>alert('xss')</script>",
                "userCode", "USR-001",
                "bankName", "Banco CO"
        ));

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(greenMail.getSmtp().getPort());

        MailProperties properties = new MailProperties(
                true,
                "127.0.0.1",
                greenMail.getSmtp().getPort(),
                "",
                "",
                "no-reply@banco.co",
                new MailProperties.Relay(10, 1000L, 3),
                new MailProperties.Executor(1, 1, 10)
        );

        JavaMailEmailDispatcher dispatcher = new JavaMailEmailDispatcher(sender, properties);
        dispatcher.dispatch(new EmailMessage("evt-1", "cliente@banco.co", "Bienvenido", htmlBody));

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();

        Message[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("cliente@banco.co");
        assertThat(received[0].getFrom()[0].toString()).contains("no-reply@banco.co");
        assertThat(received[0].getSubject()).isEqualTo("Bienvenido");

        String content = String.valueOf(received[0].getContent());
        assertThat(content).doesNotContain("<script>");
        assertThat(content).contains("&lt;script&gt;");
    }
}
