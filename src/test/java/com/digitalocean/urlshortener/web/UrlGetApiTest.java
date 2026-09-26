package com.digitalocean.urlshortener.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.digitalocean.urlshortener.config.AppProperties;
import com.digitalocean.urlshortener.service.UrlGoneException;
import com.digitalocean.urlshortener.service.UrlNotFoundException;
import com.digitalocean.urlshortener.service.UrlShortenerService;
import com.digitalocean.urlshortener.web.dto.UrlResponse;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {UrlController.class, RedirectController.class})
@Import(GlobalExceptionHandler.class)
class UrlGetApiTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UrlShortenerService urlShortenerService;
  @MockitoBean private AppProperties appProperties;

  @BeforeEach
  void stubPublicBaseUrl() {
    when(appProperties.getPublicBaseUrl()).thenReturn("");
  }

  @Test
  void metadataReturns200WithoutCallingRedirectResolve() throws Exception {
    when(urlShortenerService.getMetadata(eq("metaCode"), anyString()))
        .thenReturn(
            new UrlResponse(
                "metaCode",
                "http://localhost/metaCode",
                "https://example.com/meta",
                Instant.parse("2026-09-26T10:00:00Z"),
                null,
                true,
                0));

    mockMvc
        .perform(get("/api/v1/urls/metaCode"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("metaCode"))
        .andExpect(jsonPath("$.originalUrl").value("https://example.com/meta"))
        .andExpect(jsonPath("$.clickCount").value(0))
        .andExpect(jsonPath("$.active").value(true));

    verify(urlShortenerService, never()).resolveForRedirect(anyString());
  }

  @Test
  void metadataReturns404WhenMissing() throws Exception {
    when(urlShortenerService.getMetadata(eq("missing1"), anyString()))
        .thenThrow(new UrlNotFoundException("missing1"));

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

    verify(urlShortenerService, never()).getMetadata(anyString(), anyString());
  }

  @Test
  void metadataReturns404WhenInactive() throws Exception {
    when(urlShortenerService.getMetadata(eq("gone-meta"), anyString()))
        .thenThrow(new UrlNotFoundException("gone-meta"));

    mockMvc.perform(get("/api/v1/urls/gone-meta")).andExpect(status().isNotFound());
  }

  @Test
  void metadataReturns410WhenExpired() throws Exception {
    when(urlShortenerService.getMetadata(eq("old-meta"), anyString()))
        .thenThrow(new UrlGoneException("old-meta"));

    mockMvc
        .perform(get("/api/v1/urls/old-meta"))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.status").value(410));
  }

  @Test
  void redirectReturns302() throws Exception {
    when(urlShortenerService.resolveForRedirect("destCode"))
        .thenReturn("https://example.com/dest");

    mockMvc
        .perform(get("/destCode"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/dest"))
        .andExpect(header().string("Cache-Control", "no-cache"));
  }

  @Test
  void redirectReturns404WhenMissing() throws Exception {
    when(urlShortenerService.resolveForRedirect("nosuch01"))
        .thenThrow(new UrlNotFoundException("nosuch01"));

    mockMvc.perform(get("/nosuch01")).andExpect(status().isNotFound());
  }

  @Test
  void redirectReturns404ForInvalidCodePattern() throws Exception {
    // Path regex does not match → no handler → 404 (public surface)
    mockMvc.perform(get("/ab")).andExpect(status().isNotFound());
    verify(urlShortenerService, never()).resolveForRedirect(anyString());
  }

  @Test
  void redirectReturns404WhenInactive() throws Exception {
    when(urlShortenerService.resolveForRedirect("gone-redir"))
        .thenThrow(new UrlNotFoundException("gone-redir"));

    mockMvc.perform(get("/gone-redir")).andExpect(status().isNotFound());
  }

  @Test
  void redirectReturns410WhenExpired() throws Exception {
    when(urlShortenerService.resolveForRedirect("old-redir"))
        .thenThrow(new UrlGoneException("old-redir"));

    mockMvc.perform(get("/old-redir")).andExpect(status().isGone());
  }
}
