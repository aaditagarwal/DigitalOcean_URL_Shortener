package com.digitalocean.urlshortener.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UrlResponse(
    String code,
    String shortUrl,
    String originalUrl,
    Instant createdAt,
    Instant expiresAt,
    boolean active,
    long clickCount) {}
