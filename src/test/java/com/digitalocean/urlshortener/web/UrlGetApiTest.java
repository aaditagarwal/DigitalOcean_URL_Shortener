package com.digitalocean.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.digitalocean.urlshortener.persistence.ShortUrlRepository;
import com.digitalocean.urlshortener.persistence.entity.ShortUrlEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class UrlGetApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ShortUrlRepository repository;

  @Test
  void metadataReturns200WithoutIncrementingClicks() throws Exception {
    String code = createShortUrl("https://example.com/meta");

    mockMvc
        .perform(get("/api/v1/urls/" + code))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.originalUrl").value("https://example.com/meta"))
        .andExpect(jsonPath("$.clickCount").value(0))
        .andExpect(jsonPath("$.active").value(true));

    mockMvc
        .perform(get("/api/v1/urls/" + code))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clickCount").value(0));
  }

  @Test
  void metadataReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/v1/urls/missing1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void metadataReturns400ForInvalidCode() throws Exception {
    mockMvc
        .perform(get("/api/v1/urls/ab"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void metadataReturns404WhenInactive() throws Exception {
    ShortUrlEntity inactive = new ShortUrlEntity("gone-meta", "https://example.com", null);
    inactive.setActive(false);
    repository.saveAndFlush(inactive);

    mockMvc.perform(get("/api/v1/urls/gone-meta")).andExpect(status().isNotFound());
  }

  @Test
  void metadataReturns410WhenExpiredAndSoftDeletes() throws Exception {
    ShortUrlEntity expired =
        new ShortUrlEntity(
            "old-meta", "https://example.com", Instant.now().minus(1, ChronoUnit.HOURS));
    repository.saveAndFlush(expired);

    mockMvc
        .perform(get("/api/v1/urls/old-meta"))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.status").value(410));

    assertThat(repository.findByCode("old-meta").orElseThrow().isActive()).isFalse();

    // Subsequent reads see inactive → 404
    mockMvc.perform(get("/api/v1/urls/old-meta")).andExpect(status().isNotFound());
  }

  @Test
  void redirectReturns302AndIncrementsClicks() throws Exception {
    String code = createShortUrl("https://example.com/dest");

    mockMvc
        .perform(get("/" + code))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/dest"))
        .andExpect(header().string("Cache-Control", matchesPattern(".*no-cache.*")));

    assertThat(repository.findByCode(code).orElseThrow().getClickCount()).isEqualTo(1L);

    mockMvc.perform(get("/" + code)).andExpect(status().isFound());
    assertThat(repository.findByCode(code).orElseThrow().getClickCount()).isEqualTo(2L);

    mockMvc
        .perform(get("/api/v1/urls/" + code))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clickCount").value(2));
  }

  @Test
  void redirectReturns404WhenMissing() throws Exception {
    mockMvc.perform(get("/nosuch01")).andExpect(status().isNotFound());
  }

  @Test
  void redirectReturns404ForInvalidCodePattern() throws Exception {
    // Path regex does not match → no handler → 404 (public surface)
    mockMvc.perform(get("/ab")).andExpect(status().isNotFound());
  }

  @Test
  void redirectReturns404WhenInactive() throws Exception {
    ShortUrlEntity inactive = new ShortUrlEntity("gone-redir", "https://example.com", null);
    inactive.setActive(false);
    repository.saveAndFlush(inactive);

    mockMvc.perform(get("/gone-redir")).andExpect(status().isNotFound());
  }

  @Test
  void redirectReturns410WhenExpiredAndSoftDeletes() throws Exception {
    repository.saveAndFlush(
        new ShortUrlEntity(
            "old-redir", "https://example.com", Instant.now().minus(1, ChronoUnit.HOURS)));

    mockMvc.perform(get("/old-redir")).andExpect(status().isGone());
    assertThat(repository.findByCode("old-redir").orElseThrow().getClickCount()).isZero();
    assertThat(repository.findByCode("old-redir").orElseThrow().isActive()).isFalse();

    mockMvc.perform(get("/old-redir")).andExpect(status().isNotFound());
  }

  @Test
  void healthAndApiRootStillWorkAlongsideRedirectMapping() throws Exception {
    mockMvc.perform(get("/health")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1")).andExpect(status().isOk());
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
