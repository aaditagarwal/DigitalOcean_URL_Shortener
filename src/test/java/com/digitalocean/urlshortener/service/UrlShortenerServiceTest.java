package com.digitalocean.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.digitalocean.urlshortener.persistence.ShortUrlRepository;
import com.digitalocean.urlshortener.persistence.entity.ShortUrlEntity;
import com.digitalocean.urlshortener.web.dto.CreateUrlRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class UrlShortenerServiceTest {

  private ShortUrlRepository repository;
  private CodeGenerator codeGenerator;
  private ReservedCodeChecker reservedCodeChecker;
  private UrlShortenerService service;

  @BeforeEach
  void setUp() {
    repository = mock(ShortUrlRepository.class);
    codeGenerator = mock(CodeGenerator.class);
    reservedCodeChecker = new ReservedCodeChecker();
    service = new UrlShortenerService(repository, codeGenerator, reservedCodeChecker);
  }

  @Test
  void createPersistsGeneratedCodeAndBuildsShortUrl() {
    Instant expiresAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
    when(codeGenerator.generate()).thenReturn("Ab12Cd34");
    when(repository.saveAndFlush(any(ShortUrlEntity.class)))
        .thenAnswer(
            invocation -> {
              ShortUrlEntity entity = invocation.getArgument(0);
              entity.onCreate();
              return entity;
            });

    var response =
        service.create(
            new CreateUrlRequest(" https://example.com/path ", expiresAt),
            "https://short.example/");

    assertThat(response.code()).isEqualTo("Ab12Cd34");
    assertThat(response.shortUrl()).isEqualTo("https://short.example/Ab12Cd34");
    assertThat(response.originalUrl()).isEqualTo("https://example.com/path");
    assertThat(response.expiresAt()).isEqualTo(expiresAt);
    assertThat(response.active()).isTrue();
    assertThat(response.clickCount()).isZero();
    assertThat(response.createdAt()).isNotNull();

    ArgumentCaptor<ShortUrlEntity> captor = ArgumentCaptor.forClass(ShortUrlEntity.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getOriginalUrl()).isEqualTo("https://example.com/path");
  }

  @Test
  void createRetriesOnUniqueCollisionThenSucceeds() {
    when(codeGenerator.generate()).thenReturn("dupcode1", "okcode12");
    when(repository.saveAndFlush(any(ShortUrlEntity.class)))
        .thenThrow(new DataIntegrityViolationException("dup"))
        .thenAnswer(
            invocation -> {
              ShortUrlEntity entity = invocation.getArgument(0);
              entity.onCreate();
              return entity;
            });

    var response =
        service.create(new CreateUrlRequest("https://example.com", null), "http://localhost:8080");

    assertThat(response.code()).isEqualTo("okcode12");
    verify(repository, times(2)).saveAndFlush(any(ShortUrlEntity.class));
    verify(codeGenerator, times(2)).generate();
  }

  @Test
  void createFailsAfterExhaustingAllocationAttempts() {
    when(codeGenerator.generate()).thenReturn("collision");
    when(repository.saveAndFlush(any(ShortUrlEntity.class)))
        .thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateUrlRequest("https://example.com", null), "http://localhost"))
        .isInstanceOf(ShortCodeAllocationException.class)
        .hasMessageContaining(String.valueOf(UrlShortenerService.MAX_CODE_ALLOCATION_ATTEMPTS));

    verify(repository, times(UrlShortenerService.MAX_CODE_ALLOCATION_ATTEMPTS))
        .saveAndFlush(any(ShortUrlEntity.class));
  }

  @Test
  void createWithCustomCodePersistsExactCode() {
    when(repository.saveAndFlush(any(ShortUrlEntity.class)))
        .thenAnswer(
            invocation -> {
              ShortUrlEntity entity = invocation.getArgument(0);
              entity.onCreate();
              return entity;
            });

    var response =
        service.create(
            new CreateUrlRequest("https://example.com", null, "my-link"),
            "https://short.example");

    assertThat(response.code()).isEqualTo("my-link");
    assertThat(response.shortUrl()).isEqualTo("https://short.example/my-link");
    verify(codeGenerator, never()).generate();
  }

  @Test
  void createWithCustomCodeMapsUniqueViolationToConflict() {
    when(repository.saveAndFlush(any(ShortUrlEntity.class)))
        .thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateUrlRequest("https://example.com", null, "taken"),
                    "http://localhost"))
        .isInstanceOf(CustomCodeConflictException.class)
        .hasMessageContaining("already exists");

    verify(codeGenerator, never()).generate();
  }

  @Test
  void createWithReservedCustomCodeFails() {
    assertThatThrownBy(
            () ->
                service.create(
                    new CreateUrlRequest("https://example.com", null, "health"),
                    "http://localhost"))
        .isInstanceOf(ReservedCodeException.class);

    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void blankCustomCodeFallsBackToGenerated() {
    when(codeGenerator.generate()).thenReturn("gen12345");
    when(repository.saveAndFlush(any(ShortUrlEntity.class)))
        .thenAnswer(
            invocation -> {
              ShortUrlEntity entity = invocation.getArgument(0);
              entity.onCreate();
              return entity;
            });

    var response =
        service.create(
            new CreateUrlRequest("https://example.com", null, "   "), "http://localhost");

    assertThat(response.code()).isEqualTo("gen12345");
    verify(codeGenerator).generate();
  }
}
