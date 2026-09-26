package com.digitalocean.urlshortener.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class ShortUrlSchemaTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void flywayCreatesShortUrlsTableWithExpectedColumns() {
    List<String> columns =
        jdbcTemplate.query(
            """
            SELECT LOWER(column_name)
            FROM information_schema.columns
            WHERE LOWER(table_name) = 'short_urls'
            ORDER BY ordinal_position
            """,
            (rs, rowNum) -> rs.getString(1));

    assertThat(columns)
        .containsExactly(
            "id", "code", "original_url", "created_at", "expires_at", "active", "click_count");
  }

  @Test
  void codeHasUniqueConstraint() {
    jdbcTemplate.update(
        "INSERT INTO short_urls (code, original_url, created_at, active, click_count) "
            + "VALUES ('uniq01', 'https://a.example', CURRENT_TIMESTAMP, TRUE, 0)");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO short_urls (code, original_url, created_at, active, click_count) "
                        + "VALUES ('uniq01', 'https://b.example', CURRENT_TIMESTAMP, TRUE, 0)"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void flywaySchemaHistoryRecordsVersions() {
    List<String> versions =
        jdbcTemplate.query(
            """
            SELECT CAST("version" AS VARCHAR)
            FROM "flyway_schema_history"
            WHERE "success" = TRUE
            ORDER BY "installed_rank"
            """,
            (rs, rowNum) -> rs.getString(1));

    assertThat(versions).contains("1", "2");
  }

  @Test
  void codeColumnAcceptsCustomLengthUpTo32() {
    String code32 = "a".repeat(32);
    jdbcTemplate.update(
        "INSERT INTO short_urls (code, original_url, created_at, active, click_count) "
            + "VALUES (?, 'https://long.example', CURRENT_TIMESTAMP, TRUE, 0)",
        code32);

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM short_urls WHERE code = ?", Integer.class, code32);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void defaultsAreAppliedOnInsert() {
    jdbcTemplate.update(
        "INSERT INTO short_urls (code, original_url) VALUES ('defs01', 'https://defaults.example')");

    Boolean active =
        jdbcTemplate.queryForObject(
            "SELECT active FROM short_urls WHERE code = 'defs01'", Boolean.class);
    Long clicks =
        jdbcTemplate.queryForObject(
            "SELECT click_count FROM short_urls WHERE code = 'defs01'", Long.class);
    Object createdAt =
        jdbcTemplate.queryForObject(
            "SELECT created_at FROM short_urls WHERE code = 'defs01'", Object.class);

    assertThat(active).isTrue();
    assertThat(clicks).isZero();
    assertThat(createdAt).isNotNull();
  }
}
