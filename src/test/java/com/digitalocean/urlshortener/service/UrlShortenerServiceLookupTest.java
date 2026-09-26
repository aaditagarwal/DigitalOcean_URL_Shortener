package com.digitalocean.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.digitalocean.urlshortener.persistence.ShortUrlRepository;
import com.digitalocean.urlshortener.persistence.entity.ShortUrlEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UrlShortenerServiceLookupTest {

  private ShortUrlRepository repository;
  private UrlShortenerService service;

  @BeforeEach
  void setUp() {
    repository = mock(ShortUrlRepository.class);
    service =
        new UrlShortenerService(repository, mock(CodeGenerator.class), new ReservedCodeChecker());
  }

  @Test
  void getMetadataReturnsResponseWithoutIncrement() {
    ShortUrlEntity entity = new ShortUrlEntity("abc12345", "https://example.com", null);
    entity.onCreate();
    when(repository.findByCode("abc12345")).thenReturn(Optional.of(entity));

    var response = service.getMetadata("abc12345", "https://short.example");

    assertThat(response.code()).isEqualTo("abc12345");
    assertThat(response.shortUrl()).isEqualTo("https://short.example/abc12345");
    verify(repository, never()).incrementClickCountByCode(any());
  }

  @Test
  void getMetadataThrowsNotFoundWhenMissing() {
    when(repository.findByCode("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getMetadata("missing", "http://localhost"))
        .isInstanceOf(UrlNotFoundException.class);
  }

  @Test
  void getMetadataThrowsNotFoundWhenInactive() {
    ShortUrlEntity entity = new ShortUrlEntity("inactive", "https://example.com", null);
    entity.setActive(false);
    when(repository.findByCode("inactive")).thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> service.getMetadata("inactive", "http://localhost"))
        .isInstanceOf(UrlNotFoundException.class);
  }

  @Test
  void getMetadataThrowsGoneWhenExpiredAndDeactivates() {
    ShortUrlEntity entity =
        new ShortUrlEntity(
            "expired1", "https://example.com", Instant.now().minus(1, ChronoUnit.MINUTES));
    when(repository.findByCode("expired1")).thenReturn(Optional.of(entity));
    when(repository.deactivateByCode("expired1")).thenReturn(1);

    assertThatThrownBy(() -> service.getMetadata("expired1", "http://localhost"))
        .isInstanceOf(UrlGoneException.class);
    verify(repository).deactivateByCode("expired1");
    verify(repository, never()).incrementClickCountByCode(any());
  }

  @Test
  void resolveForRedirectIncrementsClicks() {
    ShortUrlEntity entity = new ShortUrlEntity("redir001", "https://example.com/go", null);
    when(repository.findByCode("redir001")).thenReturn(Optional.of(entity));
    when(repository.incrementClickCountByCode("redir001")).thenReturn(1);

    String location = service.resolveForRedirect("redir001");

    assertThat(location).isEqualTo("https://example.com/go");
    verify(repository).incrementClickCountByCode("redir001");
  }

  @Test
  void resolveForRedirectDoesNotIncrementWhenExpiredButDeactivates() {
    ShortUrlEntity entity =
        new ShortUrlEntity(
            "expired2", "https://example.com", Instant.now().minus(1, ChronoUnit.MINUTES));
    when(repository.findByCode("expired2")).thenReturn(Optional.of(entity));
    when(repository.deactivateByCode("expired2")).thenReturn(1);

    assertThatThrownBy(() -> service.resolveForRedirect("expired2"))
        .isInstanceOf(UrlGoneException.class);
    verify(repository).deactivateByCode("expired2");
    verify(repository, never()).incrementClickCountByCode(any());
  }
}
