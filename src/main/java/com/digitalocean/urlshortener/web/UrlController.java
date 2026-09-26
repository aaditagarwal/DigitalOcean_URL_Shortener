package com.digitalocean.urlshortener.web;

import com.digitalocean.urlshortener.config.AppProperties;
import com.digitalocean.urlshortener.service.UrlShortenerService;
import com.digitalocean.urlshortener.web.dto.CreateUrlRequest;
import com.digitalocean.urlshortener.web.dto.UrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/urls")
@Validated
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

  @GetMapping("/{code}")
  @Operation(summary = "Fetch short URL metadata (does not increment click count)")
  public ResponseEntity<UrlResponse> getMetadata(
      @PathVariable
          @Pattern(
              regexp = ShortCodePatterns.CODE,
              message = "code must match ^[A-Za-z0-9_-]{3,32}$")
          String code,
      HttpServletRequest httpRequest) {
    String publicBaseUrl = resolvePublicBaseUrl(httpRequest);
    return ResponseEntity.ok(urlShortenerService.getMetadata(code, publicBaseUrl));
  }

  @DeleteMapping("/{code}")
  @Operation(summary = "Soft-delete a short URL (idempotent if already inactive)")
  public ResponseEntity<Void> delete(
      @PathVariable
          @Pattern(
              regexp = ShortCodePatterns.CODE,
              message = "code must match ^[A-Za-z0-9_-]{3,32}$")
          String code) {
    urlShortenerService.delete(code);
    return ResponseEntity.noContent().build();
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
