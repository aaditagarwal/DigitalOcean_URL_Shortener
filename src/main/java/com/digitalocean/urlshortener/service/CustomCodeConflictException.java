package com.digitalocean.urlshortener.service;

public class CustomCodeConflictException extends RuntimeException {

  private final String customCode;

  public CustomCodeConflictException(String customCode) {
    super("customCode already exists");
    this.customCode = customCode;
  }

  public String getCustomCode() {
    return customCode;
  }
}
