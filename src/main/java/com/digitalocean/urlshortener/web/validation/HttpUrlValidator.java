package com.digitalocean.urlshortener.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;

public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.isBlank()) {
      return true; // @NotBlank owns emptiness
    }
    try {
      URI uri = URI.create(value.trim());
      String scheme = uri.getScheme();
      if (scheme == null) {
        return false;
      }
      String normalized = scheme.toLowerCase();
      return ("http".equals(normalized) || "https".equals(normalized)) && uri.getHost() != null;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }
}
