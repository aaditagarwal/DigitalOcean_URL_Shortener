package com.digitalocean.urlshortener;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

  /** Tests exclude DataSource auto-config; provide a stub so ping wiring can start. */
  @MockitoBean private JdbcTemplate jdbcTemplate;

  @Autowired private MockMvc mockMvc;

  @Test
  void healthReturnsUp() throws Exception {
    mockMvc
        .perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void apiRootIsReachable() throws Exception {
    mockMvc
        .perform(get("/api/v1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.service").value("url-shortener"));
  }
}
