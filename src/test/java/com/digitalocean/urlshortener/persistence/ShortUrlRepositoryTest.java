package com.digitalocean.urlshortener.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalocean.urlshortener.persistence.entity.ShortUrlEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class ShortUrlRepositoryTest {

  @Autowired private ShortUrlRepository repository;

  @Test
  void findByCodeReturnsEmptyWhenMissing() {
    assertThat(repository.findByCode("missing")).isEmpty();
  }

  @Test
  void saveAllowsNullExpiresAt() {
    ShortUrlEntity saved =
        repository.saveAndFlush(new ShortUrlEntity("neverexp", "https://example.com", null));

    assertThat(saved.getExpiresAt()).isNull();
    assertThat(repository.findByCode("neverexp")).get().extracting(ShortUrlEntity::getExpiresAt).isNull();
  }

  @Test
  void findByCodeStillReturnsSoftDeletedRow() {
    repository.saveAndFlush(new ShortUrlEntity("gone01", "https://example.com", null));
    repository.deactivateByCode("gone01");

    assertThat(repository.findByCode("gone01"))
        .isPresent()
        .get()
        .satisfies(
            entity -> {
              assertThat(entity.isActive()).isFalse();
              assertThat(entity.getOriginalUrl()).isEqualTo("https://example.com");
            });
  }

  @Test
  void incrementClickCountReturnsZeroForUnknownCode() {
    assertThat(repository.incrementClickCountByCode("unknown")).isZero();
  }

  @Test
  void saveAndFindByCode() {
    Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
    ShortUrlEntity saved =
        repository.saveAndFlush(new ShortUrlEntity("abc1234", "https://example.com/path", expiresAt));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.isActive()).isTrue();
    assertThat(saved.getClickCount()).isZero();

    ShortUrlEntity found = repository.findByCode("abc1234").orElseThrow();
    assertThat(found.getOriginalUrl()).isEqualTo("https://example.com/path");
    assertThat(found.getExpiresAt()).isEqualTo(expiresAt);
    assertThat(found.isExpired(Instant.now())).isFalse();
    assertThat(found.isExpired(expiresAt.plusSeconds(1))).isTrue();
  }

  @Test
  void existsByCodeReflectsPersistence() {
    assertThat(repository.existsByCode("missing")).isFalse();
    repository.saveAndFlush(new ShortUrlEntity("code01", "https://example.com", null));
    assertThat(repository.existsByCode("code01")).isTrue();
  }

  @Test
  void duplicateCodeViolatesUniqueConstraint() {
    repository.saveAndFlush(new ShortUrlEntity("dupcode", "https://a.example", null));

    assertThatThrownBy(
            () -> repository.saveAndFlush(new ShortUrlEntity("dupcode", "https://b.example", null)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deactivateByCodeIsSoftDeleteAndIdempotent() {
    repository.saveAndFlush(new ShortUrlEntity("todeact", "https://example.com", null));

    assertThat(repository.deactivateByCode("todeact")).isEqualTo(1);
    assertThat(repository.findByCode("todeact")).get().extracting(ShortUrlEntity::isActive).isEqualTo(false);

    assertThat(repository.deactivateByCode("todeact")).isZero();
    assertThat(repository.deactivateByCode("nosuch")).isZero();
  }

  @Test
  void incrementClickCountOnlyWhenActive() {
    repository.saveAndFlush(new ShortUrlEntity("clicks1", "https://example.com", null));

    assertThat(repository.incrementClickCountByCode("clicks1")).isEqualTo(1);
    assertThat(repository.incrementClickCountByCode("clicks1")).isEqualTo(1);
    assertThat(repository.findByCode("clicks1")).get().extracting(ShortUrlEntity::getClickCount).isEqualTo(2L);

    repository.deactivateByCode("clicks1");
    assertThat(repository.incrementClickCountByCode("clicks1")).isZero();
    assertThat(repository.findByCode("clicks1")).get().extracting(ShortUrlEntity::getClickCount).isEqualTo(2L);
  }

  @Test
  @Sql(
      statements =
          "INSERT INTO short_urls (code, original_url, created_at, expires_at, active, click_count) "
              + "VALUES ('sqlseed', 'https://seeded.example', CURRENT_TIMESTAMP, NULL, TRUE, 5)")
  void findsRowInsertedViaSql() {
    ShortUrlEntity found = repository.findByCode("sqlseed").orElseThrow();
    assertThat(found.getClickCount()).isEqualTo(5L);
    assertThat(found.getOriginalUrl()).isEqualTo("https://seeded.example");
  }
}
