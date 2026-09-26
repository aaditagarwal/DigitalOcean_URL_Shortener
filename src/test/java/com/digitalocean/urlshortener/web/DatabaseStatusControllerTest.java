package com.digitalocean.urlshortener.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.digitalocean.urlshortener.persistence.DatabasePingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DatabaseStatusControllerTest {

  @Mock private DatabasePingService databasePingService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new DatabaseStatusController(databasePingService)).build();
  }

  @Test
  void dbStatusReturnsUpWhenPingSucceeds() throws Exception {
    when(databasePingService.ping()).thenReturn(true);

    mockMvc
        .perform(get("/api/v1/db-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.database").value("UP"))
        .andExpect(jsonPath("$.engine").value("postgresql"));
  }

  @Test
  void dbStatusReturnsDownWhenPingFails() throws Exception {
    when(databasePingService.ping()).thenReturn(false);

    mockMvc
        .perform(get("/api/v1/db-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.database").value("DOWN"));
  }
}
