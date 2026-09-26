package com.digitalocean.urlshortener.web;

import com.digitalocean.urlshortener.service.CustomCodeConflictException;
import com.digitalocean.urlshortener.service.ReservedCodeException;
import com.digitalocean.urlshortener.service.ShortCodeAllocationException;
import com.digitalocean.urlshortener.web.dto.ErrorResponse;
import com.digitalocean.urlshortener.web.dto.ErrorResponse.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<FieldErrorDetail> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream().map(this::toDetail).toList();
    ErrorResponse body =
        ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Validation failed",
            request.getRequestURI(),
            fieldErrors);
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    ErrorResponse body =
        ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Malformed JSON request body",
            request.getRequestURI());
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(CustomCodeConflictException.class)
  public ResponseEntity<ErrorResponse> handleCustomCodeConflict(
      CustomCodeConflictException ex, HttpServletRequest request) {
    ErrorResponse body =
        ErrorResponse.of(
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  @ExceptionHandler(ReservedCodeException.class)
  public ResponseEntity<ErrorResponse> handleReservedCode(
      ReservedCodeException ex, HttpServletRequest request) {
    ErrorResponse body =
        ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI());
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(ShortCodeAllocationException.class)
  public ResponseEntity<ErrorResponse> handleAllocationFailure(
      ShortCodeAllocationException ex, HttpServletRequest request) {
    ErrorResponse body =
        ErrorResponse.of(
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
  }

  private FieldErrorDetail toDetail(FieldError error) {
    return new FieldErrorDetail(error.getField(), error.getDefaultMessage());
  }
}
