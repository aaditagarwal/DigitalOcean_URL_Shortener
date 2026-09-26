package com.digitalocean.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodeGeneratorTest {

  @Test
  void generatesExpectedLengthAndCharset() {
    CodeGenerator generator = new CodeGenerator();
    String code = generator.generate();

    assertThat(code).hasSize(CodeGenerator.DEFAULT_LENGTH);
    assertThat(code).matches("^[A-Za-z0-9]+$");
  }

  @Test
  void producesDistinctCodesAcrossManyDraws() {
    CodeGenerator generator = new CodeGenerator();
    Set<String> codes = new HashSet<>();
    for (int i = 0; i < 200; i++) {
      codes.add(generator.generate());
    }
    assertThat(codes.size()).isGreaterThan(190);
  }

  @Test
  void rejectsOutOfRangeLength() {
    assertThatThrownBy(() -> new CodeGenerator(2)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CodeGenerator(33)).isInstanceOf(IllegalArgumentException.class);
  }
}
