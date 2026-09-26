package com.digitalocean.urlshortener.service;

import com.digitalocean.urlshortener.persistence.ShortUrlRepository;
import com.digitalocean.urlshortener.persistence.entity.ShortUrlEntity;
import com.digitalocean.urlshortener.web.dto.CreateUrlRequest;
import com.digitalocean.urlshortener.web.dto.UrlResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlShortenerService {

  static final int MAX_CODE_ALLOCATION_ATTEMPTS = 5;

  private final ShortUrlRepository repository;
  private final CodeGenerator codeGenerator;
  private final ReservedCodeChecker reservedCodeChecker;

  public UrlShortenerService(
      ShortUrlRepository repository,
      CodeGenerator codeGenerator,
      ReservedCodeChecker reservedCodeChecker) {
    this.repository = repository;
    this.codeGenerator = codeGenerator;
    this.reservedCodeChecker = reservedCodeChecker;
  }

  @Transactional
  public UrlResponse create(CreateUrlRequest request, String publicBaseUrl) {
    String originalUrl = request.url().trim();
    String customCode = normalizeCustomCode(request.customCode());

    if (customCode != null) {
      return createWithCustomCode(customCode, originalUrl, request.expiresAt(), publicBaseUrl);
    }
    return createWithGeneratedCode(originalUrl, request.expiresAt(), publicBaseUrl);
  }

  private UrlResponse createWithCustomCode(
      String customCode, String originalUrl, java.time.Instant expiresAt, String publicBaseUrl) {
    reservedCodeChecker.requireNotReserved(customCode);
    try {
      ShortUrlEntity saved =
          repository.saveAndFlush(new ShortUrlEntity(customCode, originalUrl, expiresAt));
      return toResponse(saved, publicBaseUrl);
    } catch (DataIntegrityViolationException ex) {
      throw new CustomCodeConflictException(customCode);
    }
  }

  private UrlResponse createWithGeneratedCode(
      String originalUrl, java.time.Instant expiresAt, String publicBaseUrl) {
    DataIntegrityViolationException lastCollision = null;

    for (int attempt = 1; attempt <= MAX_CODE_ALLOCATION_ATTEMPTS; attempt++) {
      String code = codeGenerator.generate();
      // Skip extremely unlikely collision with reserved names
      if (reservedCodeChecker.isReserved(code)) {
        continue;
      }
      try {
        ShortUrlEntity saved =
            repository.saveAndFlush(new ShortUrlEntity(code, originalUrl, expiresAt));
        return toResponse(saved, publicBaseUrl);
      } catch (DataIntegrityViolationException ex) {
        lastCollision = ex;
      }
    }

    throw new ShortCodeAllocationException(
        "Failed to allocate a unique short code after "
            + MAX_CODE_ALLOCATION_ATTEMPTS
            + " attempts",
        lastCollision);
  }

  static String normalizeCustomCode(String customCode) {
    if (customCode == null) {
      return null;
    }
    String trimmed = customCode.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  static UrlResponse toResponse(ShortUrlEntity entity, String publicBaseUrl) {
    String base = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    String shortUrl = base.isEmpty() ? "/" + entity.getCode() : base + "/" + entity.getCode();
    return new UrlResponse(
        entity.getCode(),
        shortUrl,
        entity.getOriginalUrl(),
        entity.getCreatedAt(),
        entity.getExpiresAt(),
        entity.isActive(),
        entity.getClickCount());
  }
}
