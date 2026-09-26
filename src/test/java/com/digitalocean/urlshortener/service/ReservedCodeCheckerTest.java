package com.digitalocean.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReservedCodeCheckerTest {

  private ReservedCodeChecker checker;

  @BeforeEach
  void setUp() {
    checker = new ReservedCodeChecker();
  }

  @ParameterizedTest
  @ValueSource(strings = {"api", "health", "actuator", "swagger-ui", "v3", "Health", "API"})
  void treatsKnownSegmentsAsReserved(String code) {
    assertThat(checker.isReserved(code)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"my-link", "abc", "product_42", "OkCode"})
  void allowsNormalCustomCodes(String code) {
    assertThat(checker.isReserved(code)).isFalse();
  }
}
