package com.aiplatform.knowledge.controller;

import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.common.result.Result;
import com.aiplatform.knowledge.entity.KnowledgeBase;
import com.aiplatform.knowledge.entity.KnowledgeDocument;
import com.aiplatform.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @PostMapping("/base")
    public Result<KnowledgeBase> createBase(@RequestBody KnowledgeBase kb) {
        return Result.success(knowledgeService.createBase(kb));
    }

    @GetMapping("/base/list")
    public Result<List<KnowledgeBase>> listBases() {
        return Result.success(knowledgeService.listBases());
    }

    @GetMapping("/document/page")
    public PageResult<KnowledgeDocument> listDocuments(@RequestParam(required = false) Long kbId, PageQuery q) {
        return knowledgeService.listDocuments(kbId, q);
    }

    @PostMapping("/document/upload")
    public Result<KnowledgeDocument> upload(@RequestParam Long kbId,
                                            @RequestPart("file") MultipartFile file) throws IOException {
        return Result.success(knowledgeService.uploadDocument(kbId, file));
    }

    @GetMapping("/search")
    public Result<List<Map<String, Object>>> search(@RequestParam Long kbId,
                                                    @RequestParam String query,
                                                    @RequestParam(defaultValue = "3") int topK) {
        return Result.success(knowledgeService.search(kbId, query, topK));
    }

    /**
     * 增强搜索：query 改写 + 多路召回 + 启发式重排。
     */
    @GetMapping("/search-enhanced")
    public Result<List<Map<String, Object>>> enhancedSearch(@RequestParam Long kbId,
                                                            @RequestParam String query,
                                                            @RequestParam(defaultValue = "5") int topK) {
        return Result.success(knowledgeService.enhancedSearch(kbId, query, topK));
    }

    @GetMapping("/search-all")
    public Result<String> searchAll(@RequestParam String query,
                                    @RequestParam(defaultValue = "3") int topK) {
        return Result.success(knowledgeService.searchAsString(query, topK));
    }

    /**
     * Embedding 端点: 把文本转成向量. 用于工作流编排的 embed 节点.
     * 演示实现: 实际项目应调 DJL/BGE/OpenAI.
     */
    @PostMapping("/embed")
    public Result<Map<String, Object>> embed(@RequestBody Map<String, Object> body) {
        Object textsObj = body.get("texts");
        Object modelObj = body.getOrDefault("model", "BAAI/bge-small-zh-v1.5");
        java.util.List<String> texts = new java.util.ArrayList<>();
        if (textsObj instanceof java.util.List) {
            for (Object o : (java.util.List<?>) textsObj) texts.add(String.valueOf(o));
        } else if (textsObj != null) {
            texts.add(textsObj.toString());
        }
        // 演示: 返回固定 512 维零向量 (避免大响应)
        int dim = 512;
        java.util.List<float[]> vectors = new java.util.ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            float[] v = new float[dim];
            // 简单 hash 制造差异
            int h = texts.get(i).hashCode();
            for (int j = 0; j < dim; j++) v[j] = ((float) ((h + j) % 100)) / 100f - 0.5f;
            vectors.add(v);
        }
        Map<String, Object> ret = new java.util.HashMap<>();
        ret.put("model", modelObj);
        ret.put("dim", dim);
        ret.put("count", vectors.size());
        ret.put("vectors", vectors);
        return Result.success(ret);
    }

    /**
     * 向量索引: 把 vectors 写入 Milvus/ES/Chroma. 演示实现.
     */
    @PostMapping("/vector/index")
    public Result<Map<String, Object>> vectorIndex(@RequestBody Map<String, Object> body) {
        String backend = (String) body.getOrDefault("backend", "milvus");
        String collection = (String) body.getOrDefault("collection", "vec_v1");
        String metric = (String) body.getOrDefault("metric", "cosine");
        Object vectors = body.get("vectors");
        int count = vectors instanceof java.util.List ? ((java.util.List<?>) vectors).size() : 0;
        Map<String, Object> ret = new java.util.HashMap<>();
        ret.put("backend", backend);
        ret.put("collection", collection);
        ret.put("metric", metric);
        ret.put("indexed", count);
        ret.put("status", "indexed");
        return Result.success(ret);
    }

    /**
     * 文档切片: sliding window. 演示实现.
     */
    @PostMapping("/chunk")
    public Result<Map<String, Object>> chunk(@RequestBody Map<String, Object> body) {
        String text = (String) body.getOrDefault("text", "");
        int chunkSize = Integer.parseInt(String.valueOf(body.getOrDefault("chunkSize", 256)));
        int overlap = Integer.parseInt(String.valueOf(body.getOrDefault("overlap", 32)));
        String by = (String) body.getOrDefault("by", "sentence");
        java.util.List<String> chunks = new java.util.ArrayList<>();
        if (text.isEmpty()) {
            chunks.add("示例 chunk 1");
            chunks.add("示例 chunk 2");
        } else {
            // 简化: 按 chunkSize 字符切
            for (int i = 0; i < text.length(); i += chunkSize - overlap) {
                int end = Math.min(i + chunkSize, text.length());
                chunks.add(text.substring(i, end));
                if (end >= text.length()) break;
            }
        }
        Map<String, Object> ret = new java.util.HashMap<>();
        ret.put("chunks", chunks);
        ret.put("count", chunks.size());
        ret.put("by", by);
        return Result.success(ret);
    }

    @DeleteMapping("/document/{id}")
    public Result<Void> deleteDoc(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return Result.success();
    }

    /**
     * ★ P1-3 修复: 删除知识库 (级联删下含文档).
     */
    @DeleteMapping("/base/{id}")
    public Result<Void> deleteBase(@PathVariable Long id) {
        knowledgeService.deleteBase(id);
        return Result.success();
    }

    /**
     * 知识库服务健康检查 (供 Dashboard 监控).
     */
    @GetMapping("/health")
    public Result<java.util.Map<String, Object>> health() {
        java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("status", "UP");
        r.put("service", "ai-platform-knowledge");
        r.put("bases", knowledgeService.listBases().size());
        r.put("time", java.time.LocalDateTime.now().toString());
        return Result.success(r);
    }
}
