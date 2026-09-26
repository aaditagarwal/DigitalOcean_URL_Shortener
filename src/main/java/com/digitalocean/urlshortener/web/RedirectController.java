package com.digitalocean.urlshortener.web;

import com.digitalocean.urlshortener.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Redirect")
public class RedirectController {

  private final UrlShortenerService urlShortenerService;

  public RedirectController(UrlShortenerService urlShortenerService) {
    this.urlShortenerService = urlShortenerService;
  }

  @GetMapping("/{code:" + ShortCodePatterns.CODE_PATH + "}")
  @Operation(summary = "Redirect to the original URL (increments click count)")
  public ResponseEntity<Void> redirect(@PathVariable String code) {
    String originalUrl = urlShortenerService.resolveForRedirect(code);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(originalUrl))
        .cacheControl(CacheControl.noCache())
        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
        .build();
  }
}
