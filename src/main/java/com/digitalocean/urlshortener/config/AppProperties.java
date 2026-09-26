package com.digitalocean.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

  /**
   * Absolute public origin used to build short URLs (no trailing slash), e.g.
   * {@code https://short.example}. When blank, the create API derives the base from the incoming
   * request.
   */
  private String publicBaseUrl = "";

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String publicBaseUrl) {
    this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
  }
}
