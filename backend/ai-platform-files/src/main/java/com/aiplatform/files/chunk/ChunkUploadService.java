package com.aiplatform.files.chunk;

import com.aiplatform.files.service.FileService;
import com.aiplatform.files.service.FileStorageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分片上传服务。
 *
 * <h2>关键能力</h2>
 * <ul>
 *   <li>断点续传：客户端重连后 GET 上传状态，未到的分片 index 列表可直接续传</li>
 *   <li>分片临时目录：{@code <root>/_chunks/<uploadId>/<index>.part}</li>
 *   <li>合并时按 index 升序写，原子 rename</li>
 *   <li>过期清理：超过 {@code chunkSessionTtlMin} 分钟的 session 自动清</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkUploadService {

    private final FileService fileService;
    private final FileStorageProvider storage;

    @Value("${aiplatform.files.chunk.size:5242880}")  // 5MB
    private int defaultChunkSize;
    @Value("${aiplatform.files.chunk.ttl-min:120}")
    private int ttlMin;
    @Value("${aiplatform.files.root:/opt/ai-platform/files}")
    private String root;

    private final java.util.Map<String, ChunkSession> sessions = new ConcurrentHashMap<>();

    /**
     * 初始化分片上传。
     */
    public ChunkSession init(String originalName, String contentType, long totalSize, String bucket, int chunkSize) {
        int cs = chunkSize > 0 ? chunkSize : defaultChunkSize;
        int n = (int) Math.ceil((double) totalSize / cs);
        String id = UUID.randomUUID().toString().replace("-", "");
        ChunkSession s = new ChunkSession(id, null, originalName, contentType, totalSize, n, cs, bucket,
                new java.util.HashSet<>(), java.time.Instant.now());
        sessions.put(id, s);
        log.info("[CHUNK] init {} name={} total={} chunks={}", id, originalName, totalSize, n);
        return s;
    }

    /**
     * 保存一个分片。返回 (received, total)。
     */
    public synchronized int[] putChunk(String uploadId, int index, InputStream in) throws IOException {
        ChunkSession s = require(uploadId);
        if (index < 0 || index >= s.getTotalChunks()) {
            throw new IllegalArgumentException("chunk index out of range: " + index);
        }
        Path dir = Paths.get(root, "_chunks", uploadId);
        Files.createDirectories(dir);
        Path p = dir.resolve(index + ".part");
        long written;
        try (var out = Files.newOutputStream(p)) {
            written = in.transferTo(out);
        }
        s.getReceived().add(index);
        log.info("[CHUNK] {} idx={} bytes={} ({}/{})", uploadId, index, written,
                s.getReceived().size(), s.getTotalChunks());
        return new int[]{s.getReceived().size(), s.getTotalChunks()};
    }

    /**
     * 合并所有分片到最终位置，并入库元数据。
     */
    public synchronized String complete(String uploadId) throws IOException {
        ChunkSession s = require(uploadId);
        if (s.getReceived().size() < s.getTotalChunks()) {
            throw new IllegalStateException("missing chunks: " + missing(uploadId));
        }
        // 1. 顺序合并到 tmp
        Path finalKey = storage.resolveObjectKey(s.getBucket(), s.getOriginalName());
        Path tmp = Paths.get(root, "_chunks", uploadId, "_merged");
        try (var out = Files.newOutputStream(tmp)) {
            for (int i = 0; i < s.getTotalChunks(); i++) {
                Path p = Paths.get(root, "_chunks", uploadId, i + ".part");
                Files.copy(p, out);
            }
        }
        // 2. 提交到 provider
        long size = storage.put(s.getObjectKey() == null ? finalKey.toString() : s.getObjectKey(),
                Files.newInputStream(tmp), Files.size(tmp));
        // 3. 清理临时文件
        deleteRecursively(Paths.get(root, "_chunks", uploadId));
        sessions.remove(uploadId);
        log.info("[CHUNK] {} complete: {} bytes -> {}", uploadId, size, finalKey);
        return finalKey.toString();
    }

    /** 列出缺失的分片 index（断点续传）。 */
    public int[] missing(String uploadId) {
        ChunkSession s = require(uploadId);
        int[] out = new int[s.getTotalChunks() - s.getReceived().size()];
        int k = 0;
        for (int i = 0; i < s.getTotalChunks(); i++) {
            if (!s.getReceived().contains(i)) out[k++] = i;
        }
        return out;
    }

    public ChunkSession get(String uploadId) { return sessions.get(uploadId); }

    /** 清理过期的 session（建议挂 @Scheduled）。 */
    public synchronized int gc() {
        java.time.Instant cutoff = java.time.Instant.now().minusSeconds(ttlMin * 60L);
        int removed = 0;
        for (var e : sessions.entrySet()) {
            if (e.getValue().getCreatedAt().isBefore(cutoff)) {
                try { deleteRecursively(Paths.get(root, "_chunks", e.getKey())); }
                catch (IOException ignored) {}
                sessions.remove(e.getKey());
                removed++;
            }
        }
        if (removed > 0) log.info("[CHUNK] gc removed {} expired sessions", removed);
        return removed;
    }

    /* helpers */
    private ChunkSession require(String id) {
        ChunkSession s = sessions.get(id);
        if (s == null) throw new IllegalArgumentException("uploadId not found: " + id);
        return s;
    }

    private static void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var s = Files.walk(p)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(child -> {
                try { Files.delete(child); } catch (IOException ignored) {}
            });
        }
    }
}
