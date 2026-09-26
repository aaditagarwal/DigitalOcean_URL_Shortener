package com.digitalocean.urlshortener.web.dto;

import com.digitalocean.urlshortener.web.validation.HttpUrl;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateUrlRequest(
    @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        @HttpUrl
        String url,
    @Future(message = "expiresAt must be in the future") Instant expiresAt,
    @Size(max = 32, message = "customCode must be at most 32 characters")
        @Pattern(
            regexp = "^[A-Za-z0-9_-]{3,32}$",
            message = "customCode must match ^[A-Za-z0-9_-]{3,32}$")
        String customCode) {

  /** Convenience for auto-generate creates in tests/callers. */
  public CreateUrlRequest(String url, Instant expiresAt) {
    this(url, expiresAt, null);
  }
}
