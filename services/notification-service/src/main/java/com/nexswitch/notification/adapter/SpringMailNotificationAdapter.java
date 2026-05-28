package com.nexswitch.notification.adapter;

import com.nexswitch.domain.port.outbound.NotificationPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

// LEARN: JavaMailSender abstracts SMTP — MailHog captures in dev, no real email sent.
//        In production, configure SES/SendGrid SMTP credentials via environment variables;
//        the domain port is unaware of which provider is used.
@Component
@Profile("!test")
public class SpringMailNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SpringMailNotificationAdapter.class);

    private static final String FROM_ADDRESS = "noreply@nexswitch.local";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public SpringMailNotificationAdapter(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender     = mailSender;
        this.templateEngine = templateEngine;
    }

    @Override
    public void send(String recipient, String subject, String templateName, Map<String, Object> templateVars) {
        // LEARN: Thymeleaf Context maps Java objects to template variables; locale sets date/number formatting.
        Context ctx = new Context(Locale.forLanguageTag("en-IN"));
        if (templateVars != null) {
            templateVars.forEach(ctx::setVariable);
        }

        String htmlContent = templateEngine.process(templateName, ctx);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(FROM_ADDRESS);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = isHtml
            mailSender.send(message);
            log.info("notification.email.sent recipient={} template={}", recipient, templateName);
        } catch (MailException | MessagingException e) {
            // LEARN: Best-effort notification — email failure must not fail the primary payment flow.
            //        Log the error for alerting but do NOT rethrow; caller continues normally.
            log.error("notification.email.failed recipient={} template={} error={}",
                    recipient, templateName, e.getMessage());
        }
    }
}
