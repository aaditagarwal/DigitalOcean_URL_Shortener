package com.digitalocean.urlshortener.service;

public class ShortCodeAllocationException extends RuntimeException {

  public ShortCodeAllocationException(String message) {
    super(message);
  }

  public ShortCodeAllocationException(String message, Throwable cause) {
    super(message, cause);
  }
}
