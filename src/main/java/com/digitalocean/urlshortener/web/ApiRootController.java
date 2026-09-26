package com.digitalocean.urlshortener.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Meta")
public class ApiRootController {

  @GetMapping("/api/v1")
  @Operation(summary = "API root — confirms the service is reachable")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "API identity",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = Map.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "service": "url-shortener",
                              "status": "ready",
                              "docs": "/swagger-ui.html"
                            }
                            """)))
  })
  public ResponseEntity<Map<String, String>> root() {
    return ResponseEntity.ok(
        Map.of(
            "service", "url-shortener",
            "status", "ready",
            "docs", "/swagger-ui.html"));
  }
}
