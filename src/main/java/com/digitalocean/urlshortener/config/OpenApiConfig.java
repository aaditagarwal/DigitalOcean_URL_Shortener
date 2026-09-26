package com.digitalocean.urlshortener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI urlShortenerOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("URL Shortener API")
                .description("Create, resolve, and manage short URLs")
                .version("v1"));
  }
}
