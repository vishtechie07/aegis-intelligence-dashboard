package com.aegis.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RenderDatabaseEnvironmentPostProcessorTest {

  private final RenderDatabaseEnvironmentPostProcessor processor =
      new RenderDatabaseEnvironmentPostProcessor();

  @Test
  void toJdbcUrl_prefixesPostgresqlScheme() {
    assertThat(RenderDatabaseEnvironmentPostProcessor.toJdbcUrl(
            "postgresql://user:pass@host:5432/aegis"))
        .isEqualTo("jdbc:postgresql://user:pass@host:5432/aegis");
  }

  @Test
  void toJdbcUrl_leavesJdbcUnchanged() {
    String jdbc = "jdbc:postgresql://localhost:5432/aegis";
    assertThat(RenderDatabaseEnvironmentPostProcessor.toJdbcUrl(jdbc)).isEqualTo(jdbc);
  }

  @Test
  void extractCredentials_parsesUserAndPassword() {
    Map<String, Object> props = new HashMap<>();
    RenderDatabaseEnvironmentPostProcessor.extractCredentials(
        "jdbc:postgresql://neondb_owner:secret@ep-pooler.neon.tech/neondb?sslmode=require", props);
    assertThat(props.get("spring.datasource.username")).isEqualTo("neondb_owner");
    assertThat(props.get("spring.datasource.password")).isEqualTo("secret");
  }

  @Test
  void postProcessEnvironment_mapsDatabaseUrlWhenNoExplicitDatasource() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty(
        "DATABASE_URL", "postgresql://aegis:secret@postgres:5432/aegis");

    processor.postProcessEnvironment(env, new SpringApplication());

    assertThat(env.getProperty("spring.datasource.url"))
        .isEqualTo("jdbc:postgresql://aegis:secret@postgres:5432/aegis");
  }

  @Test
  void postProcessEnvironment_skipsWhenSpringDatasourceUrlSet() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("DATABASE_URL", "postgresql://ignored:5432/db");
    env.setProperty("SPRING_DATASOURCE_URL", "jdbc:postgresql://explicit:5432/db");

    processor.postProcessEnvironment(env, new SpringApplication());

    assertThat(env.getPropertySources().contains(
            RenderDatabaseEnvironmentPostProcessor.PROPERTY_SOURCE))
        .isFalse();
  }
}
