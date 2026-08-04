package com.project.costestimator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.mail.health.MailHealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.security.enabled=false")
class CostestimatorApplicationTests {
    @Autowired ApplicationContext context;

    @Test
    void applicationContextStarts() {
        assertThat(context.getBeansOfType(MailHealthIndicator.class)).isEmpty();
    }
}
