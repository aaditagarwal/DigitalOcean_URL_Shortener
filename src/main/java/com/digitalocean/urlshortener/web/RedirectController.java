package com.digitalocean.urlshortener.web;

import com.digitalocean.urlshortener.service.UrlShortenerService;
import com.digitalocean.urlshortener.web.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
  @ApiResponses({
    @ApiResponse(
        responseCode = "302",
        description = "Found — follow Location to original URL",
        headers = {
          @Header(
              name = HttpHeaders.LOCATION,
              description = "Original long URL",
              schema = @Schema(type = "string", example = "https://example.com/long")),
          @Header(
              name = HttpHeaders.CACHE_CONTROL,
              description = "Always no-cache",
              schema = @Schema(type = "string", example = "no-cache"))
        }),
    @ApiResponse(
        responseCode = "404",
        description = "Missing, inactive, or invalid code",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "timestamp": "2026-09-26T10:00:00Z",
                              "status": 404,
                              "error": "Not Found",
                              "message": "Short URL not found: nosuch01",
                              "path": "/nosuch01"
                            }
                            """))),
    @ApiResponse(
        responseCode = "410",
        description = "Expired (lazy soft-deleted)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "timestamp": "2026-09-26T10:00:00Z",
                              "status": 410,
                              "error": "Gone",
                              "message": "Short URL has expired: old-redir",
                              "path": "/old-redir"
                            }
                            """)))
  })
  public ResponseEntity<Void> redirect(@PathVariable String code) {
    String originalUrl = urlShortenerService.resolveForRedirect(code);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(originalUrl))
        .cacheControl(CacheControl.noCache())
        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
        .build();
  }
}
