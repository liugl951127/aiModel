package com.aiplatform.model.controller;

import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.Result;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.model.entity.ModelRegistry;
import com.aiplatform.model.service.ModelExportService;
import com.aiplatform.model.service.ModelRegistryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型导出 — 三种格式本地可用:
 *
 * <ul>
 *   <li><b>ONNX</b> — 用 onnxruntime (Python/C++/JS 都支持)</li>
 *   <li><b>GGUF</b> — 用 llama.cpp / Ollama (CPU 推理友好)</li>
 *   <li><b>PyTorch</b> — .bin / .safetensors (Python transformers)</li>
 * </ul>
 *
 * <p>每种格式 zip 包: 模型 + tokenizer + manifest.json + README + 示例推理代码</p>
 *
 * <p>使用: POST /api/model/export/{id}?format=onnx|gguf|pytorch
 *        GET  /api/model/export/{id}/download?format=...  直接下载</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/model/export")
@RequiredArgsConstructor
public class ModelExportController {

    private final ModelExportService exportService;
    private final ModelRegistryService modelService;

    /** 列出模型支持的所有导出格式. */
    @GetMapping("/{modelId}/formats")
    public Result<List<String>> listFormats(@PathVariable Long modelId) {
        return Result.success(exportService.listSupportedFormats(modelId));
    }

    /**
     * 触发导出, 返回 bundle zip 路径 + 大小 + manifest.
     * body 可选 {format: "onnx"|"gguf"|"pytorch", includeTokenizer: true, includeSample: true}.
     */
    @PostMapping("/{modelId}")
    public Result<Map<String, Object>> export(
            @PathVariable Long modelId,
            @RequestParam(defaultValue = "onnx") String format,
            @RequestBody(required = false) Map<String, Object> body) {
        boolean includeTokenizer = body == null || !Boolean.FALSE.equals(body.get("includeTokenizer"));
        boolean includeSample = body == null || !Boolean.FALSE.equals(body.get("includeSample"));
        String bundlePath = exportService.exportBundle(modelId, format, includeTokenizer, includeSample);
        Path p = Paths.get(bundlePath);
        long size = 0;
        try { size = Files.size(p); } catch (IOException e) { /* 文件刚生成, 读不到时 size=0 */ }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("modelId", modelId);
        r.put("format", format);
        r.put("bundlePath", bundlePath);
        r.put("fileName", p.getFileName().toString());
        r.put("sizeBytes", size);
        r.put("downloadUrl", "/api/model/export/" + modelId + "/download?format=" + format);
        return Result.success(r);
    }

    /**
     * 直接下载 zip (浏览器触发文件下载).
     */
    @GetMapping("/{modelId}/download")
    public void download(@PathVariable Long modelId,
                         @RequestParam(defaultValue = "onnx") String format,
                         HttpServletResponse resp) {
        // 先确保 zip 已生成
        String bundlePath = exportService.exportBundle(modelId, format, true, true);
        Path zip = Paths.get(bundlePath);
        if (!Files.exists(zip)) {
            throw new BusinessException(ResultCode.FAIL, "导出文件不存在, 请先 POST /api/model/export/" + modelId);
        }
        resp.setContentType("application/zip");
        resp.setHeader("Content-Disposition",
                "attachment; filename=\"" + zip.getFileName() + "\"");
        resp.setHeader("Cache-Control", "no-cache");
        try (OutputStream os = resp.getOutputStream()) {
            Files.copy(zip, os);
            log.info("[EXPORT-DL] model={} format={} bytes={}", modelId, format, Files.size(zip));
        } catch (IOException e) {
            log.error("[EXPORT-DL] 失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.FAIL, "下载失败: " + e.getMessage());
        }
    }

    /**
     * 仅返回 manifest.json (小 JSON, 描述模型元数据, 用于前端展示).
     */
    @GetMapping("/{modelId}/manifest")
    public Result<Map<String, Object>> manifest(@PathVariable Long modelId) {
        ModelRegistry m = modelService.getById(modelId);
        if (m == null) return Result.fail(404, "model not found: " + modelId);
        Map<String, Object> mf = new LinkedHashMap<>();
        mf.put("modelCode", m.getModelCode());
        mf.put("modelName", m.getModelName());
        mf.put("version", m.getVersion());
        mf.put("framework", m.getFramework());
        mf.put("parameterCount", m.getParameterCount());
        mf.put("language", m.getLanguage());
        mf.put("contextLength", m.getContextLength());
        mf.put("exportFormat", m.getExportFormat());
        mf.put("tags", m.getTags());
        mf.put("storagePath", m.getStoragePath());
        mf.put("status", m.getStatus());
        return Result.success(mf);
    }
}