package com.digitalocean.urlshortener.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin connectivity check used during DB wiring. Domain repositories come later.
 */
@Component
@ConditionalOnBean(JdbcTemplate.class)
public class DatabasePingService {

  private final JdbcTemplate jdbcTemplate;

  public DatabasePingService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean ping() {
    Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    return result != null && result == 1;
  }
}
