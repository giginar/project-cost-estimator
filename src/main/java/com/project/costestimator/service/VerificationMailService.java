package com.project.costestimator.service;

import com.project.costestimator.domain.AppUser;
import com.project.costestimator.dto.AuthModels.MailOutboxView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationMailService {
    private final ObjectProvider<JavaMailSender> mailSender;
    private final List<MailOutboxView> developmentOutbox = new CopyOnWriteArrayList<>();

    @Value("${app.mail.delivery-enabled:false}") private boolean deliveryEnabled;
    @Value("${app.mail.from:no-reply@costestimator.local}") private String from;
    @Value("${app.mail.verification-base-url:http://localhost:4200}") private String verificationBaseUrl;

    public void sendVerification(AppUser user, String rawToken) {
        String verificationUrl = verificationBaseUrl + "/?verify=" + rawToken;
        String subject = "Verify your Cost Estimator account";
        String body = "Hello " + user.getFullName() + ",\n\nVerify your account using this link:\n" + verificationUrl
                + "\n\nThis link expires in 24 hours.";
        if (!deliveryEnabled) {
            developmentOutbox.removeIf(message -> message.recipient().equalsIgnoreCase(user.getEmail()));
            developmentOutbox.add(new MailOutboxView(user.getEmail(), subject, verificationUrl));
            log.info("Development verification email for {}: {}", user.getEmail(), verificationUrl);
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) throw new IllegalStateException("Email delivery is enabled but SMTP is not configured");
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(user.getEmail()); message.setSubject(subject); message.setText(body);
        sender.send(message);
    }

    public void sendPasswordReset(AppUser user, String rawToken) {
        String resetUrl = verificationBaseUrl + "/?reset=" + rawToken;
        String subject = "Reset your Cost Estimator password";
        String body = "Hello " + user.getFullName() + ",\n\nReset your password using this one-time link:\n" + resetUrl
                + "\n\nThis link expires in 30 minutes. If you did not request this, you can ignore this email.";
        if (!deliveryEnabled) {
            developmentOutbox.removeIf(message -> message.recipient().equalsIgnoreCase(user.getEmail()) && message.subject().startsWith("Reset"));
            developmentOutbox.add(new MailOutboxView(user.getEmail(), subject, resetUrl));
            log.info("Development password reset email for {}: {}", user.getEmail(), resetUrl);
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) throw new IllegalStateException("Email delivery is enabled but SMTP is not configured");
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(user.getEmail()); message.setSubject(subject); message.setText(body);
        sender.send(message);
    }

    public List<MailOutboxView> developmentOutbox() { return List.copyOf(developmentOutbox); }
}
