package com.aegis.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessorApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Maps {@code DATABASE_URL} (postgresql://…) to Spring JDBC settings after config files load.
 */
public class RenderDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE = "renderDatabase";
    private static final Logger log = Logger.getLogger(RenderDatabaseEnvironmentPostProcessor.class.getName());

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String explicit = environment.getProperty("SPRING_DATASOURCE_URL");
        if (explicit != null && !explicit.isBlank()) {
            return;
        }
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            if (environment.getProperty("RENDER") != null) {
                log.warning("RENDER is set but DATABASE_URL and SPRING_DATASOURCE_URL are missing — "
                        + "using application.yml default (localhost). Set DATABASE_URL to your Neon pooled URL.");
            }
            return;
        }

        String jdbcUrl = toJdbcUrl(databaseUrl.trim());
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", jdbcUrl);
        extractCredentials(jdbcUrl, props);
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, props));
        log.info("Datasource configured from DATABASE_URL (host: " + hostFromJdbc(jdbcUrl) + ")");
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

    static void extractCredentials(String jdbcUrl, Map<String, Object> props) {
        int schemeEnd = jdbcUrl.indexOf("://");
        if (schemeEnd < 0) {
            return;
        }
        int at = jdbcUrl.lastIndexOf('@');
        int slash = jdbcUrl.indexOf('/', schemeEnd + 3);
        if (at <= schemeEnd || slash <= at) {
            return;
        }
        String userInfo = jdbcUrl.substring(schemeEnd + 3, at);
        int colon = userInfo.indexOf(':');
        if (colon <= 0) {
            return;
        }
        props.put("spring.datasource.username", decode(userInfo.substring(0, colon)));
        props.put("spring.datasource.password", decode(userInfo.substring(colon + 1)));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String hostFromJdbc(String jdbcUrl) {
        int schemeEnd = jdbcUrl.indexOf("://");
        if (schemeEnd < 0) {
            return "unknown";
        }
        int at = jdbcUrl.lastIndexOf('@');
        int slash = jdbcUrl.indexOf('/', schemeEnd + 3);
        String authority = at > schemeEnd ? jdbcUrl.substring(at + 1, slash > at ? slash : jdbcUrl.length())
                : jdbcUrl.substring(schemeEnd + 3, slash > schemeEnd ? slash : jdbcUrl.length());
        int colon = authority.indexOf(':');
        return colon > 0 ? authority.substring(0, colon) : authority;
    }

    /** After {@link EnvironmentPostProcessorApplicationListener} loads application.yml. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 11;
    }
}
