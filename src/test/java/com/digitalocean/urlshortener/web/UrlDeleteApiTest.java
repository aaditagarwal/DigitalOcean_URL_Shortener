package com.digitalocean.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.digitalocean.urlshortener.persistence.ShortUrlRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UrlDeleteApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ShortUrlRepository repository;

  @Test
  void deleteReturns204AndStopsRedirectAndMetadata() throws Exception {
    String code = createShortUrl("https://example.com/to-delete");

    mockMvc.perform(delete("/api/v1/urls/" + code)).andExpect(status().isNoContent());

    assertThat(repository.findByCode(code).orElseThrow().isActive()).isFalse();

    mockMvc.perform(get("/api/v1/urls/" + code)).andExpect(status().isNotFound());
    mockMvc.perform(get("/" + code)).andExpect(status().isNotFound());
  }

  @Test
  void deleteIsIdempotentWhenAlreadyInactive() throws Exception {
    String code = createShortUrl("https://example.com/idempotent");

    mockMvc.perform(delete("/api/v1/urls/" + code)).andExpect(status().isNoContent());
    mockMvc.perform(delete("/api/v1/urls/" + code)).andExpect(status().isNoContent());
  }

  @Test
  void deleteReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(delete("/api/v1/urls/missing99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void deleteReturns400ForInvalidCode() throws Exception {
    mockMvc.perform(delete("/api/v1/urls/ab")).andExpect(status().isBadRequest());
  }

  private String createShortUrl(String url) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/urls")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"" + url + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("code").asText();
  }
}
