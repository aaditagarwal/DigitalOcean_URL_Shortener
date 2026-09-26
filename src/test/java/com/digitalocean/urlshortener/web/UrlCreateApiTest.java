package com.digitalocean.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.digitalocean.urlshortener.persistence.ShortUrlRepository;
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
class UrlCreateApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ShortUrlRepository repository;

  @Test
  void createReturns201WithGeneratedCodeAndPersistsRow() throws Exception {
    Instant expiresAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
    String body =
        """
        {"url":"https://example.com/hello","expiresAt":"%s"}
        """
            .formatted(expiresAt);

    MvcResult result =
        mockMvc
            .perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern("/api/v1/urls/[A-Za-z0-9]{8}")))
            .andExpect(jsonPath("$.code").value(matchesPattern("[A-Za-z0-9]{8}")))
            .andExpect(jsonPath("$.originalUrl").value("https://example.com/hello"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.clickCount").value(0))
            .andExpect(jsonPath("$.expiresAt").exists())
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.shortUrl").value(matchesPattern(".*/[A-Za-z0-9]{8}")))
            .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    String code = json.get("code").asText();
    assertThat(repository.findByCode(code)).isPresent();
    assertThat(repository.findByCode(code).orElseThrow().getOriginalUrl())
        .isEqualTo("https://example.com/hello");
  }

  @Test
  void createWithoutExpiresAtSucceeds() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.expiresAt").doesNotExist());
  }

  @Test
  void createWithCustomCodeReturnsExactCode() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"url\":\"https://example.com/custom\",\"customCode\":\"my-launch\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/urls/my-launch"))
        .andExpect(jsonPath("$.code").value("my-launch"))
        .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/my-launch"));

    assertThat(repository.findByCode("my-launch")).isPresent();
  }

  @Test
  void createWithDuplicateCustomCodeReturns409() throws Exception {
    String payload =
        "{\"url\":\"https://example.com/a\",\"customCode\":\"taken-code\"}";
    mockMvc
        .perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/b\",\"customCode\":\"taken-code\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.message").value("customCode already exists"));
  }

  @Test
  void createWithReservedCustomCodeReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"customCode\":\"health\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(matchesPattern(".*reserved.*")));
  }

  @Test
  void createWithInvalidCustomCodeSyntaxReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"customCode\":\"ab\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[?(@.field=='customCode')]").exists());
  }

  @Test
  void createRejectsMissingUrl() throws Exception {
    mockMvc
        .perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("url"));
  }

  @Test
  void createRejectsNonHttpUrl() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"javascript:alert(1)\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[?(@.field=='url')]").exists());
  }

  @Test
  void createRejectsPastExpiresAt() throws Exception {
    Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
    mockMvc
        .perform(
            post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"url\":\"https://example.com\",\"expiresAt\":\"%s\"}".formatted(past)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[?(@.field=='expiresAt')]").exists());
  }

  @Test
  void createRejectsMalformedJson() throws Exception {
    mockMvc
        .perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
  }

  @Test
  void createTrimsUrlWhitespace() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"  https://example.com/trim  \"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.originalUrl").value("https://example.com/trim"));
  }
}
