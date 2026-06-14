package com.aiplatform.knowledge.service;

import com.aiplatform.common.entity.PageQuery;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.knowledge.chunker.TextChunker;
import com.aiplatform.knowledge.entity.KnowledgeBase;
import com.aiplatform.knowledge.entity.KnowledgeDocument;
import com.aiplatform.knowledge.es.KnowledgeSearchService;
import com.aiplatform.knowledge.mapper.KnowledgeBaseMapper;
import com.aiplatform.knowledge.mapper.KnowledgeDocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeDocumentMapper docMapper;
    private final TextChunker chunker;
    private final KnowledgeSearchService searchService;

    @Value("${aiplatform.knowledge.storage-path:/opt/ai-platform/kb}")
    private String storageRoot;

    public KnowledgeBase createBase(KnowledgeBase kb) {
        if (kb.getIndexName() == null) {
            kb.setIndexName("kb-" + UUID.randomUUID().toString().substring(0, 8).toLowerCase());
        }
        if (kb.getStatus() == null) kb.setStatus(1);
        searchService.ensureIndex(kb.getIndexName());
        kbMapper.insert(kb);
        return kb;
    }

    public List<KnowledgeBase> listBases() {
        return kbMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .orderByDesc(KnowledgeBase::getCreateTime));
    }

    public PageResult<KnowledgeDocument> listDocuments(Long kbId, PageQuery q) {
        Page<KnowledgeDocument> p = q.toPage();
        LambdaQueryWrapper<KnowledgeDocument> w = new LambdaQueryWrapper<>();
        if (kbId != null) w.eq(KnowledgeDocument::getKbId, kbId);
        w.orderByDesc(KnowledgeDocument::getCreateTime);
        return PageResult.of(docMapper.selectPage(p, w));
    }

    public KnowledgeDocument uploadDocument(Long kbId, MultipartFile file) throws IOException {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) throw new BusinessException(ResultCode.NOT_FOUND, "知识库不存在");

        File root = new File(storageRoot);
        if (!root.exists()) root.mkdirs();
        File dest = new File(root, kb.getIndexName() + "-" + System.currentTimeMillis() + "-" + file.getOriginalFilename());
        file.transferTo(dest);

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setKbId(kbId);
        doc.setDocCode("D-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        doc.setDocName(file.getOriginalFilename());
        doc.setDocType(file.getContentType());
        doc.setSizeBytes(file.getSize());
        doc.setStoragePath(dest.getAbsolutePath());
        doc.setStatus(1);
        docMapper.insert(doc);

        // Parse + chunk + index
        try {
            String text = chunker.extract(dest);
            List<String> chunks = chunker.chunk(text);
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("docId", doc.getId());
                meta.put("docName", doc.getDocName());
                meta.put("chunkIndex", i);
                searchService.indexDocument(kb.getIndexName(), doc.getId() + "-" + i, chunks.get(i), meta);
            }
            doc.setChunkCount(chunks.size());
            doc.setStatus(2);
            docMapper.updateById(doc);
            log.info("[KB] indexed {} chunks for doc {}", chunks.size(), doc.getId());
        } catch (Exception e) {
            log.error("[KB] index failed for doc {}: {}", doc.getId(), e.getMessage());
            doc.setStatus(0);
            doc.setErrorMessage(e.getMessage());
            docMapper.updateById(doc);
        }
        return doc;
    }

    public List<Map<String, Object>> search(Long kbId, String query, int topK) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) throw new BusinessException(ResultCode.NOT_FOUND, "知识库不存在");
        return searchService.search(kb.getIndexName(), query, topK);
    }

    public String searchAsString(String query, int topK) {
        // Cross-kb search used by the agent tool. Iterates over all bases.
        List<KnowledgeBase> bases = kbMapper.selectList(null);
        StringBuilder sb = new StringBuilder();
        for (KnowledgeBase kb : bases) {
            List<Map<String, Object>> hits = searchService.search(kb.getIndexName(), query, topK);
            for (Map<String, Object> h : hits) {
                sb.append("[知识库:").append(kb.getKbName()).append("]");
                Object content = h.get("content");
                if (content != null) sb.append(content);
                sb.append("\n");
            }
        }
        return sb.length() == 0 ? "(未找到相关知识)" : sb.toString();
    }

    public void deleteDocument(Long id) {
        docMapper.deleteById(id);
    }
}
