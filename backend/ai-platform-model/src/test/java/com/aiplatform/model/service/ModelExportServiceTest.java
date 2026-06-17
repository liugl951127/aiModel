package com.aiplatform.model.service;

import com.aiplatform.model.entity.ModelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ModelExportService 单元测试 (mock modelService, 不写真 MySQL).
 *
 * <p>验证三种格式 zip 生成 (onnx/gguf/pytorch) + 文件结构 + manifest 内容.</p>
 */
class ModelExportServiceTest {

    @TempDir
    Path tempDir;

    private ModelRegistryService modelService;
    private ModelExportService exportService;

    @BeforeEach
    void setUp() {
        modelService = mock(ModelRegistryService.class);
        exportService = new ModelExportService(modelService);
        // 注入临时目录, 避免污染 /opt
        ReflectionTestUtils.setField(exportService, "exportRoot", tempDir.toString());
    }

    @Test
    void testListSupportedFormats() {
        ModelRegistry m = new ModelRegistry();
        m.setId(1L);
        when(modelService.getById(1L)).thenReturn(m);
        assertEquals(3, exportService.listSupportedFormats(1L).size());
    }

    @Test
    void testExportOnnxBundle() throws Exception {
        ModelRegistry m = new ModelRegistry();
        m.setId(10L);
        m.setModelCode("minigpt");
        m.setModelName("MiniGPT");
        m.setVersion("v1.0");
        m.setStatus("ready");
        m.setFramework("pytorch");
        m.setParameterCount(124_000_000L);
        m.setLanguage("zh");
        m.setContextLength("2048");
        when(modelService.getById(10L)).thenReturn(m);

        String zipPath = exportService.exportBundle(10L, "onnx", true, true);
        Path zip = Path.of(zipPath);
        assertTrue(Files.exists(zip));
        assertTrue(Files.size(zip) > 100, "zip 不能为空");

        // 验证 zip 内容
        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(zip))) {
            var entries = new java.util.HashMap<String, byte[]>();
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                entries.put(e.getName(), zis.readAllBytes());
                zis.closeEntry();
            }
            assertTrue(entries.containsKey("manifest.json"), "必须有 manifest.json");
            assertTrue(entries.containsKey("model.onnx"), "必须有 model.onnx (占位)");
            assertTrue(entries.containsKey("tokenizer/config.json"), "必须有 tokenizer");
            assertTrue(entries.containsKey("README.md"), "必须有 README");
            assertTrue(entries.containsKey("serve.py"), "必须有 serve.py 示例");

            String manifest = new String(entries.get("manifest.json"));
            assertTrue(manifest.contains("\"model_code\":\"minigpt\""));
            assertTrue(manifest.contains("\"export_format\":\"onnx\""));
            assertTrue(manifest.contains("\"weight_file\":\"model.onnx\""));
        }

        verify(modelService).recordExport(eq(10L), anyString(), eq("onnx-bundle"));
    }

    @Test
    void testExportGgufBundle() throws Exception {
        ModelRegistry m = new ModelRegistry();
        m.setId(11L);
        m.setModelCode("qwen");
        m.setModelName("Qwen");
        m.setVersion("v2.0");
        m.setStatus("ready");
        m.setFramework("pytorch");
        m.setParameterCount(1_800_000_000L);
        when(modelService.getById(11L)).thenReturn(m);

        String zipPath = exportService.exportBundle(11L, "gguf", false, false);
        Path zip = Path.of(zipPath);
        assertTrue(Files.exists(zip));
        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(zip))) {
            var entries = new java.util.HashMap<String, byte[]>();
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                entries.put(e.getName(), zis.readAllBytes());
                zis.closeEntry();
            }
            assertTrue(entries.containsKey("model.gguf"));
            assertFalse(entries.containsKey("tokenizer/config.json"), "传 false 就不带 tokenizer");
            assertFalse(entries.containsKey("serve.py"));
        }
    }

    @Test
    void testExportPytorchBundle() throws Exception {
        ModelRegistry m = new ModelRegistry();
        m.setId(12L);
        m.setModelCode("llama");
        m.setModelName("Llama");
        m.setVersion("v3");
        m.setStatus("ready");
        m.setFramework("pytorch");
        when(modelService.getById(12L)).thenReturn(m);

        String zipPath = exportService.exportBundle(12L, "pytorch", true, true);
        Path zip = Path.of(zipPath);
        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(zip))) {
            var names = new java.util.HashSet<String>();
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                names.add(e.getName());
                zis.closeEntry();
            }
            assertTrue(names.contains("pytorch_model.bin"));
            assertTrue(names.contains("manifest.json"));
        }
    }

    @Test
    void testExportFailsIfNotReady() {
        ModelRegistry m = new ModelRegistry();
        m.setId(13L);
        m.setStatus("training");
        when(modelService.getById(13L)).thenReturn(m);
        assertThrows(Exception.class, () -> exportService.exportBundle(13L, "onnx", true, true));
    }

    @Test
    void testExportFailsIfModelNotFound() {
        when(modelService.getById(99L)).thenReturn(null);
        assertThrows(Exception.class, () -> exportService.exportBundle(99L, "onnx", true, true));
    }
}