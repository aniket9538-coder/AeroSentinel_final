package com.aerosentinel.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot EnvironmentPostProcessor that normalizes the database connection URL and credentials
 * from DATABASE_URL or spring.datasource.url before any data source beans are initialized.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String rawUrl = environment.getProperty("DATABASE_URL");
        if (rawUrl == null || rawUrl.isBlank()) {
            rawUrl = environment.getProperty("spring.datasource.url");
        }

        if (rawUrl != null && !rawUrl.isBlank()) {
            String explicitUser = environment.getProperty("DATABASE_USERNAME");
            String explicitPass = environment.getProperty("DATABASE_PASSWORD");

            DatabaseUrlNormalizer.NormalizedDatabaseConfig normalized =
                    DatabaseUrlNormalizer.normalize(rawUrl, explicitUser, explicitPass);

            Map<String, Object> properties = new HashMap<>();
            if (normalized.jdbcUrl() != null) {
                properties.put("spring.datasource.url", normalized.jdbcUrl());
            }
            if (normalized.username() != null) {
                properties.put("spring.datasource.username", normalized.username());
            }
            if (normalized.password() != null) {
                properties.put("spring.datasource.password", normalized.password());
            }

            if (!properties.isEmpty()) {
                environment.getPropertySources().addFirst(
                        new MapPropertySource("normalizedDatabaseProperties", properties)
                );
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
