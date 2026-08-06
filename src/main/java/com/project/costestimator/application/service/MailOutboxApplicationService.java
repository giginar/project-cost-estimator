package com.project.costestimator.application.service;

import com.project.costestimator.application.port.in.MailOutboxQuery;
import com.project.costestimator.application.port.out.MailDeliveryPort;
import com.project.costestimator.dto.AuthModels.MailOutboxView;

import java.util.List;

public final class MailOutboxApplicationService implements MailOutboxQuery {
    private final MailDeliveryPort mail;

    public MailOutboxApplicationService(MailDeliveryPort mail) {
        this.mail = mail;
    }

    @Override
    public List<MailOutboxView> developmentOutbox() {
        return mail.developmentOutbox();
    }
}
