package com.digitalocean.urlshortener.service;

import com.digitalocean.urlshortener.persistence.ShortUrlRepository;
import com.digitalocean.urlshortener.persistence.entity.ShortUrlEntity;
import com.digitalocean.urlshortener.web.dto.CreateUrlRequest;
import com.digitalocean.urlshortener.web.dto.UrlResponse;
import java.time.Instant;
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

  /** Metadata lookup — does not increment click count. */
  @Transactional(noRollbackFor = UrlGoneException.class)
  public UrlResponse getMetadata(String code, String publicBaseUrl) {
    return toResponse(requireResolvable(code), publicBaseUrl);
  }

  /**
   * Public redirect resolve — increments click count for active, non-expired codes.
   *
   * @return original URL for the {@code Location} header
   */
  @Transactional(noRollbackFor = UrlGoneException.class)
  public String resolveForRedirect(String code) {
    ShortUrlEntity entity = requireResolvable(code);
    repository.incrementClickCountByCode(code);
    return entity.getOriginalUrl();
  }

  /**
   * Soft-delete by code. Missing → {@link UrlNotFoundException}. Already inactive → no-op
   * (idempotent).
   */
  @Transactional
  public void delete(String code) {
    if (!repository.existsByCode(code)) {
      throw new UrlNotFoundException(code);
    }
    repository.deactivateByCode(code);
  }

  /**
   * Active + not expired → entity. Missing/inactive → {@link UrlNotFoundException}. Expired →
   * lazy soft-delete ({@code active=false}) then {@link UrlGoneException} (first hit after expiry
   * is {@code 410}; later reads are {@code 404}).
   */
  private ShortUrlEntity requireResolvable(String code) {
    ShortUrlEntity entity =
        repository.findByCode(code).orElseThrow(() -> new UrlNotFoundException(code));
    if (!entity.isActive()) {
      throw new UrlNotFoundException(code);
    }
    if (entity.isExpired(Instant.now())) {
      repository.deactivateByCode(code);
      throw new UrlGoneException(code);
    }
    return entity;
  }

  private UrlResponse createWithCustomCode(
      String customCode, String originalUrl, Instant expiresAt, String publicBaseUrl) {
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
      String originalUrl, Instant expiresAt, String publicBaseUrl) {
    DataIntegrityViolationException lastCollision = null;

    for (int attempt = 1; attempt <= MAX_CODE_ALLOCATION_ATTEMPTS; attempt++) {
      String code = codeGenerator.generate();
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
