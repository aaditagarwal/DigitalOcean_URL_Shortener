package com.digitalocean.urlshortener.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.digitalocean.urlshortener.persistence.ShortUrlRepository;
import com.digitalocean.urlshortener.persistence.entity.ShortUrlEntity;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Map-backed {@link ShortUrlRepository} double for unit tests — no JDBC, H2, or Flyway.
 */
public final class InMemoryShortUrlRepository {

  private InMemoryShortUrlRepository() {}

  public static ShortUrlRepository create() {
    Map<String, ShortUrlEntity> byCode = new ConcurrentHashMap<>();
    ShortUrlRepository repository = mock(ShortUrlRepository.class);

    when(repository.findByCode(anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(byCode.get(invocation.getArgument(0))));

    when(repository.existsByCode(anyString()))
        .thenAnswer(invocation -> byCode.containsKey(invocation.getArgument(0)));

    when(repository.deactivateByCode(anyString()))
        .thenAnswer(
            invocation -> {
              ShortUrlEntity entity = byCode.get(invocation.getArgument(0));
              if (entity == null || !entity.isActive()) {
                return 0;
              }
              entity.setActive(false);
              return 1;
            });

    when(repository.incrementClickCountByCode(anyString()))
        .thenAnswer(
            invocation -> {
              ShortUrlEntity entity = byCode.get(invocation.getArgument(0));
              if (entity == null || !entity.isActive()) {
                return 0;
              }
              entity.setClickCount(entity.getClickCount() + 1);
              return 1;
            });

    when(repository.saveAndFlush(any(ShortUrlEntity.class)))
        .thenAnswer(
            invocation -> {
              ShortUrlEntity entity = invocation.getArgument(0);
              if (byCode.containsKey(entity.getCode())) {
                throw new DataIntegrityViolationException("duplicate code: " + entity.getCode());
              }
              entity.onCreate();
              byCode.put(entity.getCode(), entity);
              return entity;
            });

    return repository;
  }
}
