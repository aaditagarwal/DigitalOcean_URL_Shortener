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
@Tag(name = "Health")
public class HealthController {

  @GetMapping("/health")
  @Operation(summary = "Liveness / readiness probe for App Platform and CI smoke tests")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Service is up",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = Map.class),
                examples =
                    @ExampleObject(
                        value =
                            """
                            {"status":"UP"}
                            """)))
  })
  public ResponseEntity<Map<String, String>> health() {
    return ResponseEntity.ok(Map.of("status", "UP"));
  }
}
