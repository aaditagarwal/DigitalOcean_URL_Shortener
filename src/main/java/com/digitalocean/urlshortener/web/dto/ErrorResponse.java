package com.digitalocean.urlshortener.web.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldErrorDetail> fieldErrors) {

  public record FieldErrorDetail(String field, String message) {}

  public static ErrorResponse of(
      int status, String error, String message, String path, List<FieldErrorDetail> fieldErrors) {
    return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
  }

  public static ErrorResponse of(int status, String error, String message, String path) {
    return of(status, error, message, path, null);
  }
}
