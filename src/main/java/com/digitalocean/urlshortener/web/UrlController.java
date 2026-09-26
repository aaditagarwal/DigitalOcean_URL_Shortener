package com.digitalocean.urlshortener.web;

import com.digitalocean.urlshortener.config.AppProperties;
import com.digitalocean.urlshortener.service.UrlShortenerService;
import com.digitalocean.urlshortener.web.dto.CreateUrlRequest;
import com.digitalocean.urlshortener.web.dto.UrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/urls")
@Tag(name = "URLs")
public class UrlController {

  private final UrlShortenerService urlShortenerService;
  private final AppProperties appProperties;

  public UrlController(UrlShortenerService urlShortenerService, AppProperties appProperties) {
    this.urlShortenerService = urlShortenerService;
    this.appProperties = appProperties;
  }

  @PostMapping
  @Operation(summary = "Create a short URL (auto-generated or optional customCode)")
  public ResponseEntity<UrlResponse> create(
      @Valid @RequestBody CreateUrlRequest request, HttpServletRequest httpRequest) {
    String publicBaseUrl = resolvePublicBaseUrl(httpRequest);
    UrlResponse body = urlShortenerService.create(request, publicBaseUrl);
    URI location = URI.create("/api/v1/urls/" + body.code());
    return ResponseEntity.status(HttpStatus.CREATED).location(location).body(body);
  }

  private String resolvePublicBaseUrl(HttpServletRequest request) {
    String configured = appProperties.getPublicBaseUrl();
    if (configured != null && !configured.isBlank()) {
      return configured.replaceAll("/+$", "");
    }
    return ServletUriComponentsBuilder.fromRequestUri(request)
        .replacePath(null)
        .replaceQuery(null)
        .build()
        .toUriString();
  }
}
