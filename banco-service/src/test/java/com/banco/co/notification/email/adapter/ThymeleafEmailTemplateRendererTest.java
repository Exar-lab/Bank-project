package com.banco.co.notification.email.adapter;

import com.banco.co.notification.email.config.ThymeleafEmailConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ThymeleafEmailTemplateRendererTest {

    @Test
    void testRender_WhenUserInputContainsScript_ThenEscapesHtml() {
        ThymeleafEmailConfig config = new ThymeleafEmailConfig();
        var resolver = config.emailTemplateResolver(new StandardEnvironment());
        var engine = config.emailTemplateEngine(resolver);
        ThymeleafEmailTemplateRenderer renderer = new ThymeleafEmailTemplateRenderer(engine);

        String html = renderer.render("email/welcome", Map.of(
                "recipientName", "<script>alert('xss')</script>",
                "userCode", "USR-001",
                "bankName", "Banco CO"
        ));

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }
}
