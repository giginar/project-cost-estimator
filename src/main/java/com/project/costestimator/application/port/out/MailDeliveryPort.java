package com.project.costestimator.application.port.out;

import com.project.costestimator.domain.AppUser;
import com.project.costestimator.dto.AuthModels.MailOutboxView;

import java.util.List;

public interface MailDeliveryPort {
    void sendVerification(AppUser user, String rawToken);
    void sendPasswordReset(AppUser user, String rawToken);
    List<MailOutboxView> developmentOutbox();
}
