package com.digitalocean.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.digitalocean.urlshortener.persistence.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UrlShortenerServiceDeleteTest {

  private ShortUrlRepository repository;
  private UrlShortenerService service;

  @BeforeEach
  void setUp() {
    repository = mock(ShortUrlRepository.class);
    service =
        new UrlShortenerService(repository, mock(CodeGenerator.class), new ReservedCodeChecker());
  }

  @Test
  void deleteDeactivatesExistingCode() {
    when(repository.existsByCode("abc12345")).thenReturn(true);
    when(repository.deactivateByCode("abc12345")).thenReturn(1);

    service.delete("abc12345");

    verify(repository).deactivateByCode("abc12345");
  }

  @Test
  void deleteIsIdempotentWhenAlreadyInactive() {
    when(repository.existsByCode("abc12345")).thenReturn(true);
    when(repository.deactivateByCode("abc12345")).thenReturn(0);

    service.delete("abc12345");

    verify(repository).deactivateByCode("abc12345");
  }

  @Test
  void deleteThrowsNotFoundWhenMissing() {
    when(repository.existsByCode("missing")).thenReturn(false);

    assertThatThrownBy(() -> service.delete("missing")).isInstanceOf(UrlNotFoundException.class);
    verify(repository, never()).deactivateByCode("missing");
  }
}
