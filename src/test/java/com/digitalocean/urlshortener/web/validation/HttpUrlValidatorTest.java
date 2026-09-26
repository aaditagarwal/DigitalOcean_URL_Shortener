package com.digitalocean.urlshortener.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class HttpUrlValidatorTest {

  private HttpUrlValidator validator;

  @BeforeEach
  void setUp() {
    validator = new HttpUrlValidator();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://example.com",
        "http://example.com/path",
        "https://sub.example.com:8443/a?b=1",
        "  https://example.com/trim  "
      })
  void acceptsHttpAndHttps(String value) {
    assertThat(validator.isValid(value, null)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "javascript:alert(1)",
        "data:text/plain,hi",
        "file:///tmp/x",
        "ftp://example.com",
        "not-a-url",
        "https://",
        "://missing-scheme.com"
      })
  void rejectsNonHttpSchemesAndMalformed(String value) {
    assertThat(validator.isValid(value, null)).isFalse();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void defersBlankToNotBlank(String value) {
    assertThat(validator.isValid(value, null)).isTrue();
  }

  @Test
  void rejectsRelativePaths() {
    assertThat(validator.isValid("/relative", null)).isFalse();
  }
}
