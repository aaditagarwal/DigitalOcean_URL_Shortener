package com.digitalocean.urlshortener.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DatabasePingServiceTest {

  @Mock private JdbcTemplate jdbcTemplate;

  @InjectMocks private DatabasePingService databasePingService;

  @Test
  void pingReturnsTrueWhenSelectOneSucceeds() {
    when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

    assertTrue(databasePingService.ping());
    verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
  }

  @Test
  void pingReturnsFalseWhenSelectOneReturnsNull() {
    when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(null);

    assertFalse(databasePingService.ping());
  }
}
