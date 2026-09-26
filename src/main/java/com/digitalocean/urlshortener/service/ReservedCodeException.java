package com.digitalocean.urlshortener.service;

public class ReservedCodeException extends RuntimeException {

  public ReservedCodeException(String customCode) {
    super("customCode is reserved: " + customCode);
  }
}
