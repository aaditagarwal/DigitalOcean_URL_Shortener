package com.digitalocean.urlshortener.persistence;

import com.digitalocean.urlshortener.persistence.entity.ShortUrlEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortUrlRepository extends JpaRepository<ShortUrlEntity, Long> {

  Optional<ShortUrlEntity> findByCode(String code);

  boolean existsByCode(String code);

  /**
   * Soft-delete: set active=false when the row exists and is still active.
   *
   * @return number of rows updated (0 if missing or already inactive)
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE ShortUrlEntity e
      SET e.active = false
      WHERE e.code = :code AND e.active = true
      """)
  int deactivateByCode(@Param("code") String code);

  /**
   * Increment click counter on redirect for an active short URL.
   *
   * @return number of rows updated (0 if missing or inactive)
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE ShortUrlEntity e
      SET e.clickCount = e.clickCount + 1
      WHERE e.code = :code AND e.active = true
      """)
  int incrementClickCountByCode(@Param("code") String code);
}
