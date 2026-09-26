-- Widen code to support custom short codes (3–32 chars)
ALTER TABLE short_urls ALTER COLUMN code TYPE VARCHAR(32);
