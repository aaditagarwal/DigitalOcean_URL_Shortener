package com.digitalocean.urlshortener.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class ShortUrlEntityTest {

  @Test
  void constructorSetsDefaults() {
    Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");
    ShortUrlEntity entity = new ShortUrlEntity("abc1234", "https://example.com", expiresAt);

    assertThat(entity.getId()).isNull();
    assertThat(entity.getCode()).isEqualTo("abc1234");
    assertThat(entity.getOriginalUrl()).isEqualTo("https://example.com");
    assertThat(entity.getExpiresAt()).isEqualTo(expiresAt);
    assertThat(entity.getCreatedAt()).isNull();
    assertThat(entity.isActive()).isTrue();
    assertThat(entity.getClickCount()).isZero();
  }

  @Test
  void onCreateSetsCreatedAtWhenMissing() {
    ShortUrlEntity entity = new ShortUrlEntity("code01", "https://example.com", null);
    Instant before = Instant.now().minusSeconds(1);

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getCreatedAt()).isAfter(before);
    assertThat(entity.getCreatedAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(1));
  }

  @Test
  void onCreateDoesNotOverwriteExistingCreatedAt() {
    ShortUrlEntity entity = new ShortUrlEntity("code01", "https://example.com", null);
    entity.onCreate();
    Instant first = entity.getCreatedAt();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isEqualTo(first);
  }

  @Test
  void isExpiredWhenExpiresAtIsNull() {
    ShortUrlEntity entity = new ShortUrlEntity("code01", "https://example.com", null);
    assertThat(entity.isExpired(Instant.now())).isFalse();
  }

  @Test
  void isExpiredWhenNowIsBeforeExpiresAt() {
    Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
    ShortUrlEntity entity = new ShortUrlEntity("code01", "https://example.com", expiresAt);

    assertThat(entity.isExpired(expiresAt.minusSeconds(1))).isFalse();
  }

  @Test
  void isExpiredWhenNowEqualsExpiresAt() {
    Instant expiresAt = Instant.parse("2026-06-01T12:00:00Z");
    ShortUrlEntity entity = new ShortUrlEntity("code01", "https://example.com", expiresAt);

    assertThat(entity.isExpired(expiresAt)).isTrue();
  }

  @Test
  void isExpiredWhenNowIsAfterExpiresAt() {
    Instant expiresAt = Instant.parse("2026-06-01T12:00:00Z");
    ShortUrlEntity entity = new ShortUrlEntity("code01", "https://example.com", expiresAt);

    assertThat(entity.isExpired(expiresAt.plusSeconds(1))).isTrue();
  }

  @Test
  void equalsUsesIdWhenPresent() {
    ShortUrlEntity a = new ShortUrlEntity("a", "https://a.example", null);
    ShortUrlEntity b = new ShortUrlEntity("b", "https://b.example", null);

    assertThat(a).isNotEqualTo(b);
    assertThat(a).isEqualTo(a);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  void settersUpdateMutableFields() {
    ShortUrlEntity entity = new ShortUrlEntity("code01", "https://example.com", null);

    entity.setActive(false);
    entity.setClickCount(9L);

    assertThat(entity.isActive()).isFalse();
    assertThat(entity.getClickCount()).isEqualTo(9L);
  }
}
