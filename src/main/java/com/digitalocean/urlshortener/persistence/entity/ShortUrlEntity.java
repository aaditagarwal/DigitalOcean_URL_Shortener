package com.digitalocean.urlshortener.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "short_urls")
public class ShortUrlEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 16)
  private String code;

  @Column(name = "original_url", nullable = false, length = 2048)
  private String originalUrl;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "click_count", nullable = false)
  private long clickCount = 0L;

  protected ShortUrlEntity() {}

  public ShortUrlEntity(String code, String originalUrl, Instant expiresAt) {
    this.code = code;
    this.originalUrl = originalUrl;
    this.expiresAt = expiresAt;
    this.active = true;
    this.clickCount = 0L;
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public boolean isExpired(Instant now) {
    return expiresAt != null && !expiresAt.isAfter(now);
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getOriginalUrl() {
    return originalUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean isActive() {
    return active;
  }

  public long getClickCount() {
    return clickCount;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public void setClickCount(long clickCount) {
    this.clickCount = clickCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ShortUrlEntity that)) {
      return false;
    }
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
