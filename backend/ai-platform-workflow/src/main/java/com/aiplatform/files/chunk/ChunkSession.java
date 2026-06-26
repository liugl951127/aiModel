package com.aiplatform.files.chunk;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * 分片上传会话。
 *
 * <p>客户端上传大文件时分片传：
 * <ol>
 *   <li>{@code POST /api/files/chunk/init} → 拿到 {@code uploadId} + 分片大小</li>
 *   <li>{@code PUT /api/files/chunk/{uploadId}?index=N} → 传第 N 片</li>
 *   <li>{@code POST /api/files/chunk/{uploadId}/complete} → 合并并入库</li>
 *   <li>{@code GET /api/files/chunk/{uploadId}} → 看哪些分片已到（断点续传）</li>
 * </ol>
 *
 * <p>进程内 ConcurrentHashMap，集群部署换 Redis Hash。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkSession {
    private String uploadId;
    private String objectKey;
    private String originalName;
    private String contentType;
    private long totalSize;
    private int totalChunks;
    private int chunkSize;
    private String bucket;
    private Set<Integer> received = new HashSet<>();
    private Instant createdAt = Instant.now();
}
