package com.digitalocean.urlshortener.web;

import com.digitalocean.urlshortener.persistence.DatabasePingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Meta")
public class DatabaseStatusController {

  private final DatabasePingService databasePingService;

  public DatabaseStatusController(DatabasePingService databasePingService) {
    this.databasePingService = databasePingService;
  }

  @GetMapping("/api/v1/db-status")
  @Operation(summary = "Verify application connectivity to Managed PostgreSQL")
  public ResponseEntity<Map<String, Object>> dbStatus() {
    boolean ok = databasePingService.ping();
    return ResponseEntity.ok(Map.of("database", ok ? "UP" : "DOWN", "engine", "postgresql"));
  }
}
