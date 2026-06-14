package com.aiplatform.files.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.files.chunk.ChunkSession;
import com.aiplatform.files.chunk.ChunkUploadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 分片上传 REST 端点。
 */
@Slf4j
@RestController
@RequestMapping("/api/files/chunk")
@RequiredArgsConstructor
public class ChunkUploadController {

    private final ChunkUploadService chunks;

    /**
     * 初始化一个分片上传会话。
     */
    @PostMapping("/init")
    public Result<ChunkSession> init(@RequestParam("originalName") String name,
                                     @RequestParam(value = "contentType", required = false) String ct,
                                     @RequestParam("totalSize") long total,
                                     @RequestParam(value = "bucket", required = false) String bucket,
                                     @RequestParam(value = "chunkSize", required = false) Integer cs) {
        return Result.success(chunks.init(name, ct, total, bucket, cs == null ? 0 : cs));
    }

    /**
     * 推送一个分片（按 index）。body 是原始分片字节。
     */
    @PutMapping("/{uploadId}")
    public Result<Map<String, Object>> put(@PathVariable("uploadId") String id,
                                           @RequestParam("index") int index,
                                           HttpServletRequest request) throws IOException {
        int[] counts = chunks.putChunk(id, index, request.getInputStream());
        Map<String, Object> m = new HashMap<>();
        m.put("received", counts[0]);
        m.put("total", counts[1]);
        return Result.success(m);
    }

    /**
     * 查询上传状态：哪些分片已到 / 缺哪些。
     */
    @GetMapping("/{uploadId}")
    public Result<Map<String, Object>> get(@PathVariable("uploadId") String id) {
        ChunkSession s = chunks.get(id);
        if (s == null) return Result.fail(404, "upload not found");
        Map<String, Object> m = new HashMap<>();
        m.put("uploadId", s.getUploadId());
        m.put("totalChunks", s.getTotalChunks());
        m.put("received", s.getReceived().size());
        m.put("missing", chunks.missing(id));
        m.put("objectKey", s.getObjectKey());
        return Result.success(m);
    }

    /**
     * 合并所有分片到最终位置，返回成功消息。
     */
    @PostMapping("/{uploadId}/complete")
    public Result<String> complete(@PathVariable("uploadId") String id) throws IOException {
        return Result.success(chunks.complete(id));
    }
}
