package com.digitalocean.urlshortener.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.digitalocean.urlshortener.config.AppProperties;
import com.digitalocean.urlshortener.service.UrlNotFoundException;
import com.digitalocean.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UrlController.class)
@Import(GlobalExceptionHandler.class)
class UrlDeleteApiTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UrlShortenerService urlShortenerService;
  @MockitoBean private AppProperties appProperties;

  @BeforeEach
  void stubPublicBaseUrl() {
    when(appProperties.getPublicBaseUrl()).thenReturn("");
  }

  @Test
  void deleteReturns204() throws Exception {
    doNothing().when(urlShortenerService).delete("delCode1");

    mockMvc.perform(delete("/api/v1/urls/delCode1")).andExpect(status().isNoContent());

    verify(urlShortenerService).delete("delCode1");
  }

  @Test
  void deleteIsIdempotentWhenServiceNoOps() throws Exception {
    doNothing().when(urlShortenerService).delete("idem01");

    mockMvc.perform(delete("/api/v1/urls/idem01")).andExpect(status().isNoContent());
    mockMvc.perform(delete("/api/v1/urls/idem01")).andExpect(status().isNoContent());
  }

  @Test
  void deleteReturns404WhenMissing() throws Exception {
    doThrow(new UrlNotFoundException("missing99")).when(urlShortenerService).delete("missing99");

    mockMvc
        .perform(delete("/api/v1/urls/missing99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void deleteReturns400ForInvalidCode() throws Exception {
    mockMvc.perform(delete("/api/v1/urls/ab")).andExpect(status().isBadRequest());
    verify(urlShortenerService, never()).delete(anyString());
  }
}
