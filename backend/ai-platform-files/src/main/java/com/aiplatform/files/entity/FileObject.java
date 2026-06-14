package com.aiplatform.files.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * File metadata record persisted in MySQL. The actual bytes are stored
 * separately by a {@link com.aiplatform.files.service.FileStorageProvider}
 * (local disk by default, S3/MinIO in production).
 *
 * <p>The {@link #objectKey} is opaque and provider-agnostic — clients only
 * ever reference files by {@link #fileId} or {@link #objectKey}.
 */
@Data
@TableName("file_object")
public class FileObject {

    /** Surrogate primary key, auto-increment. */
    @TableId(type = IdType.AUTO)
    private Long fileId;

    /** Provider-agnostic storage key (e.g. {@code "kb/2025/12/abc.pdf"}). */
    private String objectKey;

    /** Original filename as uploaded by the client. */
    private String originalName;

    /** Detected MIME type (sniffed via Tika). */
    private String contentType;

    /** File size in bytes. */
    private Long sizeBytes;

    /**
     * Logical bucket, used by the knowledge-base ingestor to scope queries.
     * Examples: {@code kb}, {@code corpus}, {@code agent-asset}.
     */
    private String bucket;

    /** SHA-256 hex digest, used for dedup and integrity checks. */
    private String sha256;

    /** Optional uploader, populated when secure-starter is on. */
    private String uploader;

    /** Uploader-supplied description / tag. */
    private String description;

    /** Current lifecycle state. See {@link Status}. */
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Allowed {@link #status} values. */
    public static final class Status {
        /** Upload is in progress (multipart chunks not yet committed). */
        public static final String UPLOADING = "uploading";
        /** Bytes are persisted and the metadata row is committed. */
        public static final String READY = "ready";
        /** Soft-deleted; bytes may still exist for a retention window. */
        public static final String DELETED = "deleted";
    }
}
