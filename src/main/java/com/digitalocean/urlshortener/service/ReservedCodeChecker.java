package com.digitalocean.urlshortener.service;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ReservedCodeChecker {

  private static final Set<String> RESERVED =
      Set.of(
          "api",
          "health",
          "actuator",
          "swagger-ui",
          "swagger-ui.html",
          "v1",
          "error",
          "favicon.ico",
          "urls");

  public boolean isReserved(String code) {
    if (code == null || code.isBlank()) {
      return false;
    }
    return RESERVED.contains(code.toLowerCase(Locale.ROOT));
  }

  public void requireNotReserved(String code) {
    if (isReserved(code)) {
      throw new ReservedCodeException(code);
    }
  }
}
