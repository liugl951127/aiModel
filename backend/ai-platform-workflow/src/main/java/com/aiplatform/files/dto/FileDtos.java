package com.aiplatform.files.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request/response DTOs for the file-server REST surface.
 *
 * <p>Kept in a single class to keep the controller signatures small.
 */
public class FileDtos {

    /**
     * Metadata returned by upload, download, query.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileInfo {
        private Long fileId;
        private String objectKey;
        private String originalName;
        private String contentType;
        private Long sizeBytes;
        private String bucket;
        private String sha256;
        private String status;
        private LocalDateTime createdAt;

        /** URL the client can use to download the bytes. */
        private String downloadUrl;
    }

    /**
     * Init request for a chunked upload. The server allocates a logical
     * file id and returns it; the client then PUTs each chunk.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitUploadRequest {
        private String originalName;
        private String contentType;
        private Long totalSize;
        private String bucket;
        private String description;
    }

    /**
     * Init response with the assigned file id and the upload endpoint
     * the client should stream bytes to.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitUploadResponse {
        private Long fileId;
        private String objectKey;
        private String uploadUrl;
    }
}
