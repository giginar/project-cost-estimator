package com.project.costestimator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI costEstimatorOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Construction Cost Estimator API")
                        .version("v1")
                        .description("Resource planning, scheduling, and cost estimation API for construction projects.")
                        .contact(new Contact().name("Cost Estimator Team"))
                        .license(new License().name("Private use")))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local development")));
    }
}
