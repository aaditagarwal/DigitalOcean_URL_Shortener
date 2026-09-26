package com.digitalocean.urlshortener.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.digitalocean.urlshortener.config.AppProperties;
import com.digitalocean.urlshortener.service.CustomCodeConflictException;
import com.digitalocean.urlshortener.service.ReservedCodeException;
import com.digitalocean.urlshortener.service.UrlShortenerService;
import com.digitalocean.urlshortener.web.dto.CreateUrlRequest;
import com.digitalocean.urlshortener.web.dto.UrlResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UrlController.class)
@Import(GlobalExceptionHandler.class)
class UrlCreateApiTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UrlShortenerService urlShortenerService;
  @MockitoBean private AppProperties appProperties;

  @BeforeEach
  void stubPublicBaseUrl() {
    when(appProperties.getPublicBaseUrl()).thenReturn("");
  }

  @Test
  void createReturns201WithGeneratedCode() throws Exception {
    Instant expiresAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
    Instant createdAt = Instant.parse("2026-09-26T10:00:00Z");
    when(urlShortenerService.create(any(CreateUrlRequest.class), anyString()))
        .thenReturn(
            new UrlResponse(
                "Ab12Cd34",
                "http://localhost/Ab12Cd34",
                "https://example.com/hello",
                createdAt,
                expiresAt,
                true,
                0));

    String body =
        """
        {"url":"https://example.com/hello","expiresAt":"%s"}
        """
            .formatted(expiresAt);

    mockMvc
        .perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/urls/Ab12Cd34"))
        .andExpect(jsonPath("$.code").value("Ab12Cd34"))
        .andExpect(jsonPath("$.originalUrl").value("https://example.com/hello"))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.clickCount").value(0))
        .andExpect(jsonPath("$.expiresAt").exists())
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.shortUrl").value("http://localhost/Ab12Cd34"));
  }

  @Test
  void createWithoutExpiresAtSucceeds() throws Exception {
    when(urlShortenerService.create(any(CreateUrlRequest.class), anyString()))
        .thenReturn(
            new UrlResponse(
                "Ab12Cd34",
                "http://localhost/Ab12Cd34",
                "https://example.com",
                Instant.parse("2026-09-26T10:00:00Z"),
                null,
                true,
                0));

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
    when(urlShortenerService.create(any(CreateUrlRequest.class), anyString()))
        .thenReturn(
            new UrlResponse(
                "my-launch",
                "http://localhost/my-launch",
                "https://example.com/custom",
                Instant.parse("2026-09-26T10:00:00Z"),
                null,
                true,
                0));

    mockMvc
        .perform(
            post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"url\":\"https://example.com/custom\",\"customCode\":\"my-launch\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/urls/my-launch"))
        .andExpect(jsonPath("$.code").value("my-launch"))
        .andExpect(jsonPath("$.shortUrl").value("http://localhost/my-launch"));
  }

  @Test
  void createWithDuplicateCustomCodeReturns409() throws Exception {
    when(urlShortenerService.create(any(CreateUrlRequest.class), anyString()))
        .thenThrow(new CustomCodeConflictException("taken-code"));

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
    when(urlShortenerService.create(any(CreateUrlRequest.class), anyString()))
        .thenThrow(new ReservedCodeException("health"));

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

    verify(urlShortenerService, never()).create(any(), anyString());
  }

  @Test
  void createRejectsMissingUrl() throws Exception {
    mockMvc
        .perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("url"));

    verify(urlShortenerService, never()).create(any(), anyString());
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

    verify(urlShortenerService, never()).create(any(), anyString());
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

    verify(urlShortenerService, never()).create(any(), anyString());
  }

  @Test
  void createRejectsMalformedJson() throws Exception {
    mockMvc
        .perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed JSON request body"));

    verify(urlShortenerService, never()).create(any(), anyString());
  }

  @Test
  void createPassesTrimmedRequestToService() throws Exception {
    when(urlShortenerService.create(any(CreateUrlRequest.class), anyString()))
        .thenReturn(
            new UrlResponse(
                "Ab12Cd34",
                "http://localhost/Ab12Cd34",
                "https://example.com/trim",
                Instant.parse("2026-09-26T10:00:00Z"),
                null,
                true,
                0));

    mockMvc
        .perform(
            post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"  https://example.com/trim  \"}"))
        .andExpect(status().isCreated());

    verify(urlShortenerService).create(any(CreateUrlRequest.class), eq("http://localhost"));
  }
}
