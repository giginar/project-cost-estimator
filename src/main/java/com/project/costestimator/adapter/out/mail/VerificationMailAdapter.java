package com.project.costestimator.adapter.out.mail;

import com.project.costestimator.application.port.out.MailDeliveryPort;
import com.project.costestimator.domain.AppUser;
import com.project.costestimator.dto.AuthModels.MailOutboxView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

@Slf4j
@Component
public class VerificationMailAdapter implements MailDeliveryPort {
    private final ObjectProvider<JavaMailSender> mailSender;
    private final List<MailOutboxView> developmentOutbox = new CopyOnWriteArrayList<>();

    @Value("${app.mail.delivery-enabled:false}")
    private boolean deliveryEnabled;

    @Value("${app.mail.from:no-reply@costestimator.local}")
    private String from;

    @Value("${app.mail.verification-base-url:http://localhost:4200}")
    private String publicBaseUrl;

    public VerificationMailAdapter(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendVerification(AppUser user, String rawToken) {
        String url = publicBaseUrl + "/?verify=" + rawToken;
        String subject = "Verify your Cost Estimator account";
        String body = "Hello " + user.getFullName()
                + ",\n\nVerify your account using this link:\n" + url
                + "\n\nThis link expires in 24 hours.";
        Predicate<MailOutboxView> previousMessage = message ->
                message.recipient().equalsIgnoreCase(user.getEmail());
        deliver(user.getEmail(), subject, body, url, previousMessage, "verification");
    }

    @Override
    public void sendPasswordReset(AppUser user, String rawToken) {
        String url = publicBaseUrl + "/?reset=" + rawToken;
        String subject = "Reset your Cost Estimator password";
        String body = "Hello " + user.getFullName()
                + ",\n\nReset your password using this one-time link:\n" + url
                + "\n\nThis link expires in 30 minutes. If you did not request this, you can ignore this email.";
        Predicate<MailOutboxView> previousMessage = message ->
                message.recipient().equalsIgnoreCase(user.getEmail())
                        && message.subject().startsWith("Reset");
        deliver(user.getEmail(), subject, body, url, previousMessage, "password reset");
    }

    @Override
    public List<MailOutboxView> developmentOutbox() {
        return List.copyOf(developmentOutbox);
    }

    private void deliver(String recipient, String subject, String body, String url,
                         Predicate<MailOutboxView> previousMessage, String messageType) {
        if (!deliveryEnabled) {
            developmentOutbox.removeIf(previousMessage);
            developmentOutbox.add(new MailOutboxView(recipient, subject, url));
            log.info("Development {} email for {}: {}", messageType, recipient, url);
            return;
        }

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("Email delivery is enabled but SMTP is not configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
