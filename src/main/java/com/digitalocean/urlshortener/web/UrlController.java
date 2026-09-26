package com.digitalocean.urlshortener.web;

import com.digitalocean.urlshortener.config.AppProperties;
import com.digitalocean.urlshortener.service.UrlShortenerService;
import com.digitalocean.urlshortener.web.dto.CreateUrlRequest;
import com.digitalocean.urlshortener.web.dto.ErrorResponse;
import com.digitalocean.urlshortener.web.dto.UrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Created",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = UrlResponse.class),
                examples =
                    @ExampleObject(
                        name = "created",
                        value =
                            """
                            {
                              "code": "Ab12Cd34",
                              "shortUrl": "http://localhost:8080/Ab12Cd34",
                              "originalUrl": "https://example.com/long",
                              "createdAt": "2026-09-26T10:00:00Z",
                              "expiresAt": "2030-01-01T00:00:00Z",
                              "active": true,
                              "clickCount": 0
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "Validation failed (url / expiresAt / customCode / reserved)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        name = "validation",
                        value =
                            """
                            {
                              "timestamp": "2026-09-26T10:00:00Z",
                              "status": 400,
                              "error": "Bad Request",
                              "message": "Validation failed",
                              "path": "/api/v1/urls",
                              "fieldErrors": [
                                {"field": "url", "message": "url is required"}
                              ]
                            }
                            """))),
    @ApiResponse(
        responseCode = "409",
        description = "customCode already exists",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples =
                    @ExampleObject(
                        name = "conflict",
                        value =
                            """
                            {
                              "timestamp": "2026-09-26T10:00:00Z",
                              "status": 409,
                              "error": "Conflict",
                              "message": "customCode already exists",
                              "path": "/api/v1/urls"
                            }
                            """)))
  })
  public ResponseEntity<UrlResponse> create(
      @Valid @RequestBody CreateUrlRequest request, HttpServletRequest httpRequest) {
    String publicBaseUrl = resolvePublicBaseUrl(httpRequest);
    UrlResponse body = urlShortenerService.create(request, publicBaseUrl);
    URI location = URI.create("/api/v1/urls/" + body.code());
    return ResponseEntity.status(HttpStatus.CREATED).location(location).body(body);
  }

  @GetMapping("/{code}")
  @Operation(summary = "Fetch short URL metadata (does not increment click count)")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Metadata",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = UrlResponse.class),
                examples =
                    @ExampleObject(
                        name = "metadata",
                        value =
                            """
                            {
                              "code": "Ab12Cd34",
                              "shortUrl": "http://localhost:8080/Ab12Cd34",
                              "originalUrl": "https://example.com/long",
                              "createdAt": "2026-09-26T10:00:00Z",
                              "active": true,
                              "clickCount": 3
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid code format",
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
                              "status": 400,
                              "error": "Bad Request",
                              "message": "Validation failed",
                              "path": "/api/v1/urls/ab"
                            }
                            """))),
    @ApiResponse(
        responseCode = "404",
        description = "Not found or inactive",
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
                              "message": "Short URL not found: missing1",
                              "path": "/api/v1/urls/missing1"
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
                              "message": "Short URL has expired: old-code",
                              "path": "/api/v1/urls/old-code"
                            }
                            """)))
  })
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
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Deleted or already inactive"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid code format",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Code does not exist",
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
                              "message": "Short URL not found: missing99",
                              "path": "/api/v1/urls/missing99"
                            }
                            """)))
  })
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
