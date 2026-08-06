package com.project.costestimator.application.port.in;

import com.project.costestimator.dto.AuthModels.MailOutboxView;

import java.util.List;

public interface MailOutboxQuery {
    List<MailOutboxView> developmentOutbox();
}
