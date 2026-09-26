package com.digitalocean.urlshortener.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class CodeGenerator {

  static final int DEFAULT_LENGTH = 8;
  private static final char[] ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

  private final SecureRandom random = new SecureRandom();
  private final int length;

  public CodeGenerator() {
    this(DEFAULT_LENGTH);
  }

  CodeGenerator(int length) {
    if (length < 3 || length > 32) {
      throw new IllegalArgumentException("code length must be between 3 and 32");
    }
    this.length = length;
  }

  public String generate() {
    char[] chars = new char[length];
    for (int i = 0; i < length; i++) {
      chars[i] = ALPHABET[random.nextInt(ALPHABET.length)];
    }
    return new String(chars);
  }
}
