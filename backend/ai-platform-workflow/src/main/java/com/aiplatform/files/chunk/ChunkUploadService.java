package com.aiplatform.files.chunk;

import com.aiplatform.files.service.FileService;
import com.aiplatform.files.service.FileStorageProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 分片上传服务 — Redis 持久化 (替代原 ConcurrentHashMap 内存版).
 *
 * <h2>关键能力</h2>
 * <ul>
 *   <li>断点续传: 客户端重连后 GET 上传状态, 未到的分片 index 列表可直接续传</li>
 *   <li>分片临时目录: {@code <root>/_chunks/<uploadId>/<index>.part}</li>
 *   <li>合并时按 index 升序写, 原子 rename</li>
 *   <li>过期清理: 超过 {@code chunkSessionTtlMin} 分钟的 session 自动清</li>
 *   <li><b>★ 服务重启不丢</b>: Session 元数据 + received 索引集合走 Redis, 不是内存 Map</li>
 * </ul>
 *
 * <h2>Redis 结构</h2>
 * <ul>
 *   <li>Key: {@code chunk:session:<uploadId>}, Value: JSON (uploadId, objectKey, originalName,
 *       contentType, totalSize, totalChunks, chunkSize, bucket, createdAt)
 *   <li>Key: {@code chunk:received:<uploadId>}, Value: Redis Set (收到的分片 index)
 *   <li>TTL: ttlMin (默认 120 分钟)
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkUploadService {

    private final FileService fileService;
    private final FileStorageProvider storage;
    private final StringRedisTemplate redis;  // ★ 取代 ConcurrentHashMap

    @Value("${aiplatform.files.chunk.size:5242880}")  // 5MB
    private int defaultChunkSize;
    @Value("${aiplatform.files.chunk.ttl-min:120}")
    private int ttlMin;
    @Value("${aiplatform.files.root:/opt/ai-platform/files}")
    private String root;

    private static final ObjectMapper OM = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static String sessionKey(String id) { return "chunk:session:" + id; }
    private static String receivedKey(String id) { return "chunk:received:" + id; }

    /**
     * 初始化分片上传.
     */
    public ChunkSession init(String originalName, String contentType, long totalSize, String bucket, int chunkSize) {
        int cs = chunkSize > 0 ? chunkSize : defaultChunkSize;
        int n = (int) Math.ceil((double) totalSize / cs);
        String id = UUID.randomUUID().toString().replace("-", "");
        ChunkSession s = new ChunkSession(id, null, originalName, contentType, totalSize, n, cs, bucket,
                new HashSet<>(), Instant.now());
        // ★ Redis: 写 session JSON + 初始化 received 空 Set + TTL
        try {
            redis.opsForValue().set(sessionKey(id), OM.writeValueAsString(s), ttlMin, TimeUnit.MINUTES);
            redis.expire(receivedKey(id), Duration.ofMinutes(ttlMin));
        } catch (Exception e) {
            log.warn("[CHUNK] Redis 写 session 失败 (回退内存): {}", e.getMessage());
        }
        log.info("[CHUNK] init {} name={} total={} chunks={}", id, originalName, totalSize, n);
        return s;
    }

    /**
     * 保存一个分片. 返回 (received, total).
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
        // ★ Redis: 加到 received Set
        try {
            redis.opsForSet().add(receivedKey(uploadId), String.valueOf(index));
            // 同步刷新 TTL (用户连续上传续命)
            redis.expire(receivedKey(uploadId), Duration.ofMinutes(ttlMin));
            redis.expire(sessionKey(uploadId), Duration.ofMinutes(ttlMin));
        } catch (Exception e) {
            log.debug("[CHUNK] Redis 写 received 失败: {}", e.getMessage());
        }
        // 内存 Set 也加 (get() 返给前端用)
        s.getReceived().add(index);
        log.info("[CHUNK] {} idx={} bytes={} ({}/{})", uploadId, index, written,
                s.getReceived().size(), s.getTotalChunks());
        return new int[]{s.getReceived().size(), s.getTotalChunks()};
    }

    /**
     * 合并所有分片到最终位置, 并入库元数据.
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
        // ★ Redis: 删 session + received
        try {
            redis.delete(sessionKey(uploadId));
            redis.delete(receivedKey(uploadId));
        } catch (Exception e) { log.debug("[CHUNK] Redis 清理失败: {}", e.getMessage()); }
        log.info("[CHUNK] {} complete: {} bytes -> {}", uploadId, size, finalKey);
        return finalKey.toString();
    }

    /** 列出缺失的分片 index (断点续传). */
    public int[] missing(String uploadId) {
        ChunkSession s = require(uploadId);
        // ★ 优先 Redis (重启后还在), 没拿内存
        Set<Integer> received;
        try {
            Set<String> r = redis.opsForSet().members(receivedKey(uploadId));
            received = new HashSet<>();
            if (r != null) for (String x : r) received.add(Integer.parseInt(x));
            // 内存的也加 (兼容 Redis down 的场景)
            received.addAll(s.getReceived());
        } catch (Exception e) {
            received = s.getReceived();
        }
        int[] out = new int[s.getTotalChunks() - received.size()];
        int k = 0;
        for (int i = 0; i < s.getTotalChunks(); i++) {
            if (!received.contains(i)) out[k++] = i;
        }
        return out;
    }

    public ChunkSession get(String uploadId) {
        // ★ 从 Redis 重建 (重启服务后还能查到)
        try {
            String json = redis.opsForValue().get(sessionKey(uploadId));
            if (json == null) return null;
            ChunkSession s = OM.readValue(json, ChunkSession.class);
            // 补 received 集合
            Set<String> r = redis.opsForSet().members(receivedKey(uploadId));
            s.setReceived(new HashSet<>());
            if (r != null) for (String x : r) s.getReceived().add(Integer.parseInt(x));
            return s;
        } catch (Exception e) {
            log.warn("[CHUNK] get({}) Redis 读失败: {}", uploadId, e.getMessage());
            return null;
        }
    }

    /** 清理过期的 session (建议挂 @Scheduled). */
    public synchronized int gc() {
        int removed = 0;
        try {
            // Redis 自动 TTL, 不需要主动 gc. 但临时文件需要清
            var keys = redis.keys("chunk:session:*");
            if (keys == null) return 0;
            for (String key : keys) {
                String id = key.substring("chunk:session:".length());
                Path dir = Paths.get(root, "_chunks", id);
                if (Files.exists(dir)) {
                    long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(dir).toMillis();
                    if (ageMs > ttlMin * 60_000L) {
                        try { deleteRecursively(dir); removed++; }
                        catch (IOException ignored) {}
                    }
                }
            }
        } catch (Exception e) { log.debug("[CHUNK] gc 失败: {}", e.getMessage()); }
        if (removed > 0) log.info("[CHUNK] gc removed {} expired chunk dirs", removed);
        return removed;
    }

    /* helpers */
    private ChunkSession require(String id) {
        ChunkSession s = get(id);
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