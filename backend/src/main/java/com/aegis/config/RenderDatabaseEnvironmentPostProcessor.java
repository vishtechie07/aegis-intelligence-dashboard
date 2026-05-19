package com.aegis.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps Render Postgres {@code DATABASE_URL} (postgresql://…) to Spring JDBC settings
 * when {@code SPRING_DATASOURCE_URL} is not set explicitly.
 */
public class RenderDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE = "renderDatabase";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        String explicit = environment.getProperty("SPRING_DATASOURCE_URL");
        if (explicit != null && !explicit.isBlank()) {
            return;
        }

        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", toJdbcUrl(databaseUrl.trim()));
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, props));
    }

    static String toJdbcUrl(String databaseUrl) {
        if (databaseUrl.startsWith("jdbc:")) {
            return databaseUrl;
        }
        if (databaseUrl.startsWith("postgres://")) {
            return "jdbc:postgresql://" + databaseUrl.substring("postgres://".length());
        }
        if (databaseUrl.startsWith("postgresql://")) {
            return "jdbc:postgresql://" + databaseUrl.substring("postgresql://".length());
        }
        return databaseUrl;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
