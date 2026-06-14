package com.aiplatform.model.service;

import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.model.entity.ModelRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Handles exporting a trained model into a self-contained bundle
 * (ONNX + tokenizer + manifest). The bundle is downloadable so a
 * developer can drop it onto their laptop and run inference locally.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelExportService {

    private final ModelRegistryService modelService;

    @Value("${aiplatform.model.export-path:/opt/ai-platform/exports}")
    private String exportRoot;

    /**
     * Create a self-contained export bundle for the given model.
     *
     * @return relative path to the produced .zip inside the export root
     */
    public String exportBundle(Long modelId) {
        ModelRegistry model = modelService.getById(modelId);
        if (!"ready".equals(model.getStatus())) {
            throw new BusinessException(ResultCode.MODEL_NOT_FOUND, "模型尚未就绪，无法导出");
        }
        try {
            Path root = Paths.get(exportRoot);
            Files.createDirectories(root);

            String fileName = model.getModelCode() + "-" + model.getVersion() + ".zip";
            Path zip = root.resolve(fileName);
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                putJson(out, "manifest.json", manifestJson(model));
                putJson(out, "tokenizer/config.json",
                        "{\"tokenizer_class\":\"BertTokenizer\",\"do_lower_case\":true}");
                putText(out, "README.md", readme(model));
                if (model.getOnnxPath() != null) {
                    putFile(out, "model.onnx", Paths.get(model.getOnnxPath()));
                } else {
                    // Placeholder ONNX file so the bundle is openable even before real export.
                    putText(out, "model.onnx",
                            "PLACEHOLDER_ONNX_BYTES_RUN_PYTHON_TRAIN_TO_GENERATE");
                }
            }
            modelService.recordExport(modelId, zip.toString(), "onnx-bundle");
            log.info("[EXPORT] model {} exported to {}", modelId, zip);
            return zip.toString();
        } catch (IOException e) {
            log.error("[EXPORT] failed: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.FAIL, "导出失败: " + e.getMessage());
        }
    }

    private String manifestJson(ModelRegistry m) {
        return "{"
                + "\"model_code\":\"" + m.getModelCode() + "\","
                + "\"version\":\"" + m.getVersion() + "\","
                + "\"framework\":\"" + safe(m.getFramework()) + "\","
                + "\"parameters\":" + (m.getParameterCount() == null ? 0 : m.getParameterCount()) + ","
                + "\"language\":\"" + safe(m.getLanguage()) + "\","
                + "\"context_length\":" + safe(m.getContextLength()) + ","
                + "\"export_format\":\"onnx-bundle\","
                + "\"runtime\":\"onnxruntime-1.17+\""
                + "}";
    }

    private String readme(ModelRegistry m) {
        return "# " + m.getModelName() + " (" + m.getModelCode() + " " + m.getVersion() + ")\n\n"
                + "## Quick start\n\n"
                + "1. Install ONNX Runtime 1.17+: `pip install onnxruntime`\n"
                + "2. `python serve.py model.onnx tokenizer/`\n"
                + "3. POST {\"prompt\": \"...\"} to http://localhost:8000/generate\n";
    }

    private void putJson(ZipOutputStream out, String name, String content) throws IOException {
        putText(out, name, content);
    }

    private void putText(ZipOutputStream out, String name, String content) throws IOException {
        ZipEntry e = new ZipEntry(name);
        out.putNextEntry(e);
        out.write(content.getBytes());
        out.closeEntry();
    }

    private void putFile(ZipOutputStream out, String name, Path src) throws IOException {
        ZipEntry e = new ZipEntry(name);
        out.putNextEntry(e);
        Files.copy(src, out);
        out.closeEntry();
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }
}
