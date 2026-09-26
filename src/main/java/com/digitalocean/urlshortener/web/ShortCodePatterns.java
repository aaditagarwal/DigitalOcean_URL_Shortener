package com.digitalocean.urlshortener.web;

/** Shared short-code path/body pattern (auto-generated and custom). */
public final class ShortCodePatterns {

  public static final String CODE = "^[A-Za-z0-9_-]{3,32}$";
  /** Spring MVC path-variable regex (no anchors). */
  public static final String CODE_PATH = "[A-Za-z0-9_-]{3,32}";

  private ShortCodePatterns() {}
}
