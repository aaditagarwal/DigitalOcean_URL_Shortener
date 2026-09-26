package com.digitalocean.urlshortener.service;

public class UrlGoneException extends RuntimeException {

  public UrlGoneException(String code) {
    super("Short URL has expired: " + code);
  }
}
