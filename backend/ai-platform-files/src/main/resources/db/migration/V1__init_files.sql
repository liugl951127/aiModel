-- ============================================================================
-- ai-platform-files schema
-- ============================================================================

CREATE DATABASE IF NOT EXISTS aiplatform_files
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE aiplatform_files;

CREATE TABLE IF NOT EXISTS file_object (
    file_id        BIGINT          NOT NULL AUTO_INCREMENT,
    object_key     VARCHAR(512)    NOT NULL,
    original_name  VARCHAR(255)    NOT NULL,
    content_type   VARCHAR(128)    NOT NULL DEFAULT 'application/octet-stream',
    size_bytes     BIGINT          NOT NULL DEFAULT 0,
    bucket         VARCHAR(64)     NOT NULL DEFAULT 'kb',
    sha256         VARCHAR(64)     NOT NULL DEFAULT '',
    uploader       VARCHAR(64)     DEFAULT NULL,
    description    VARCHAR(512)    DEFAULT NULL,
    status         VARCHAR(16)     NOT NULL DEFAULT 'ready',
    created_at     DATETIME(3)     NOT NULL,
    updated_at     DATETIME(3)     NOT NULL,
    PRIMARY KEY (file_id),
    UNIQUE KEY uk_object_key (object_key),
    KEY idx_bucket_status (bucket, status),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
