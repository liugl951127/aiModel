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

    @DeleteMapping("/document/{id}")
    public Result<Void> deleteDoc(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return Result.success();
    }
}
