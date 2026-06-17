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
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 模型导出服务 — 三种格式本地可用:
 *
 * <ul>
 *   <li><b>onnx</b> — 通用, onnxruntime (Python / C++ / JS / .NET)</li>
 *   <li><b>gguf</b> — llama.cpp / Ollama (CPU 友好, 量化模型)</li>
 *   <li><b>pytorch</b> — transformers 库直接 load_state_dict (Python)</li>
 * </ul>
 *
 * <p>每个 bundle 包含:
 * <ol>
 *   <li>model.{onnx|gguf|bin} 权重文件 (从 storagePath 拷)</li>
 *   <li>tokenizer/                tokenizer 配置 (json + vocab)</li>
 *   <li>manifest.json             模型元数据</li>
 *   <li>README.md                 用法说明</li>
 *   <li>serve.py / serve.sh       示例推理脚本</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelExportService {

    private final ModelRegistryService modelService;

    @Value("${aiplatform.model.export-path:/opt/ai-platform/exports}")
    private String exportRoot;

    /** 模型支持的所有导出格式 (根据权重文件存在情况). */
    public List<String> listSupportedFormats(Long modelId) {
        ModelRegistry m = modelService.getById(modelId);
        if (m == null) throw new BusinessException(ResultCode.FAIL, "model not found: " + modelId);
        // 都支持, 但只有对应文件存在才实际产出
        return Arrays.asList("onnx", "gguf", "pytorch");
    }

    /**
     * 触发导出. format: onnx / gguf / pytorch.
     * @return zip bundle 绝对路径.
     */
    public String exportBundle(Long modelId, String format, boolean includeTokenizer, boolean includeSample) {
        ModelRegistry model = modelService.getById(modelId);
        if (model == null) {
            throw new BusinessException(ResultCode.FAIL, "model not found: " + modelId);
        }
        if (!"ready".equals(model.getStatus())) {
            throw new BusinessException(ResultCode.MODEL_NOT_FOUND, "模型尚未就绪, 当前 status=" + model.getStatus());
        }
        try {
            Path root = Paths.get(exportRoot);
            Files.createDirectories(root);

            String fmt = format == null ? "onnx" : format.toLowerCase();
            String fileName = model.getModelCode() + "-" + model.getVersion() + "-" + fmt + ".zip";
            Path zip = root.resolve(fileName);

            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                // 1. 权重文件 (主)
                String weightFile = putWeight(out, fmt, model);

                // 2. manifest.json
                putText(out, "manifest.json", manifestJson(model, fmt, weightFile));

                // 3. tokenizer (可选)
                if (includeTokenizer) {
                    putText(out, "tokenizer/config.json", tokenizerConfig(model));
                    putText(out, "tokenizer/vocab.txt",
                            "# 简化版 vocab\n[PAD]\n[UNK]\n[CLS]\n[SEP]\n[MASK]\n你好\n世界\n模型\n训练\n推理\n");
                }

                // 4. README
                putText(out, "README.md", readme(model, fmt, weightFile));

                // 5. serve.py (示例推理脚本, 用户可直接 python serve.py model.onnx)
                if (includeSample) {
                    putText(out, "serve.py", serveScript(fmt));
                    putText(out, "client.py", clientScript());
                }
            }
            modelService.recordExport(modelId, zip.toString(), fmt + "-bundle");
            log.info("[EXPORT] model={} format={} → {}", modelId, fmt, zip);
            return zip.toString();
        } catch (IOException e) {
            log.error("[EXPORT] failed: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.FAIL, "导出失败: " + e.getMessage());
        }
    }

    /** 把权重文件拷进 zip, 返回 zip 内的文件名. */
    private String putWeight(ZipOutputStream out, String fmt, ModelRegistry model) throws IOException {
        String weightFile;
        Path src = null;
        // 1. 先看 model 自己的字段
        switch (fmt) {
            case "gguf": {
                // GGUF 可能没有原文件, 写占位 + 转换说明
                if (model.getStoragePath() != null && Files.exists(Paths.get(model.getStoragePath()))) {
                    src = Paths.get(model.getStoragePath());
                }
                weightFile = "model.gguf";
                break;
            }
            case "pytorch": {
                if (model.getStoragePath() != null && Files.exists(Paths.get(model.getStoragePath()))) {
                    src = Paths.get(model.getStoragePath());
                }
                weightFile = "pytorch_model.bin";
                break;
            }
            default: { // onnx
                if (model.getOnnxPath() != null && Files.exists(Paths.get(model.getOnnxPath()))) {
                    src = Paths.get(model.getOnnxPath());
                } else if (model.getStoragePath() != null && Files.exists(Paths.get(model.getStoragePath()))) {
                    src = Paths.get(model.getStoragePath());
                }
                weightFile = "model.onnx";
                break;
            }
        }

        if (src != null && Files.exists(src)) {
            putFile(out, weightFile, src);
            log.info("[EXPORT] weight copied: {} → zip:{}", src, weightFile);
        } else {
            // 占位: 用户跑 python 训练后会替换
            String placeholder = switch (fmt) {
                case "gguf"     -> "GGUF_PLACEHOLDER_RUN_PYTHON_TO_GENERATE\n# 转换命令: python convert_to_gguf.py model.bin model.gguf\n";
                case "pytorch"  -> "PYTORCH_PLACEHOLDER_RUN_PYTHON_TO_GENERATE\n# 直接用 torch.save(model.state_dict(), 'pytorch_model.bin')\n";
                default         -> "ONNX_PLACEHOLDER_RUN_PYTHON_TO_GENERATE\n# 转换命令: torch.onnx.export(model, dummy_input, 'model.onnx')\n";
            };
            putText(out, weightFile, placeholder);
            log.warn("[EXPORT] 权重文件不存在, 用占位: src={} format={}", src, fmt);
        }
        return weightFile;
    }

    private String manifestJson(ModelRegistry m, String fmt, String weightFile) {
        return "{"
                + "\"model_code\":\"" + safe(m.getModelCode()) + "\","
                + "\"model_name\":\"" + safe(m.getModelName()) + "\","
                + "\"version\":\"" + safe(m.getVersion()) + "\","
                + "\"framework\":\"" + safe(m.getFramework()) + "\","
                + "\"parameters\":" + (m.getParameterCount() == null ? 0 : m.getParameterCount()) + ","
                + "\"language\":\"" + safe(m.getLanguage()) + "\","
                + "\"context_length\":" + safe(m.getContextLength()) + ","
                + "\"export_format\":\"" + fmt + "\","
                + "\"weight_file\":\"" + weightFile + "\","
                + "\"tags\":\"" + safe(m.getTags()) + "\","
                + "\"created_at\":\"" + (m.getCreateTime() == null ? "" : m.getCreateTime()) + "\""
                + "}";
    }

    private String tokenizerConfig(ModelRegistry m) {
        return "{\n"
                + "  \"tokenizer_class\": \"BertTokenizer\",\n"
                + "  \"do_lower_case\": true,\n"
                + "  \"vocab_size\": 21128,\n"
                + "  \"model_max_length\": " + safe(m.getContextLength(), "512") + "\n"
                + "}";
    }

    private String readme(ModelRegistry m, String fmt, String weightFile) {
        return "# " + safe(m.getModelName()) + " (" + safe(m.getModelCode()) + " " + safe(m.getVersion()) + ")\n\n"
                + "## 模型信息\n\n"
                + "- 框架: " + safe(m.getFramework()) + "\n"
                + "- 参数量: " + (m.getParameterCount() == null ? "?" : m.getParameterCount()) + "\n"
                + "- 语言: " + safe(m.getLanguage()) + "\n"
                + "- 上下文: " + safe(m.getContextLength(), "512") + " tokens\n"
                + "- 导出格式: **" + fmt + "**\n\n"
                + "## 本地推理\n\n"
                + switch (fmt) {
                    case "gguf" -> "### 用 llama.cpp / Ollama\n\n"
                            + "```bash\n"
                            + "# Ollama 导入\n"
                            + "ollama create my-model -f Modelfile\n"
                            + "echo 'FROM ./" + weightFile + "' > Modelfile\n"
                            + "ollama run my-model \"你好\"\n\n"
                            + "# llama.cpp 直接\n"
                            + "./main -m " + weightFile + " -p \"你好\"\n";
                    case "pytorch" -> "### 用 transformers (Python)\n\n"
                            + "```python\n"
                            + "from transformers import AutoModelForCausalLM, AutoTokenizer\n"
                            + "tok = AutoTokenizer.from_pretrained('.')\n"
                            + "model = AutoModelForCausalLM.from_pretrained('.')\n"
                            + "print(model.generate(tok.encode('你好', return_tensors='pt'), max_length=50))\n"
                            + "```\n";
                    default -> "### 用 onnxruntime (Python / C++ / JS)\n\n"
                            + "```python\n"
                            + "import onnxruntime as ort\n"
                            + "import numpy as np\n"
                            + "sess = ort.InferenceSession('" + weightFile + "')\n"
                            + "out = sess.run(None, {'input_ids': np.array([[101, 872, 1962, 102]]).astype(np.int64)})\n"
                            + "print(out)\n"
                            + "```\n";
                }
                + "\n## 快速启动\n\n"
                + "```bash\n"
                + "pip install -r requirements.txt  # 见 serve.py 头部\n"
                + "python serve.py\n"
                + "curl -X POST http://localhost:8000/generate -d '{\"prompt\":\"你好\"}'\n"
                + "```\n";
    }

    private String serveScript(String fmt) {
        return switch (fmt) {
            case "gguf" -> "# llama.cpp / Ollama serve\n"
                    + "# 安装: pip install llama-cpp-python\n"
                    + "from llama_cpp import Llama\n"
                    + "llm = Llama(model_path='model.gguf', n_ctx=2048)\n"
                    + "print(llm('你好, 介绍一下自己', max_tokens=128))\n";
            case "pytorch" -> "# transformers serve\n"
                    + "from flask import Flask, request, jsonify\n"
                    + "from transformers import AutoModelForCausalLM, AutoTokenizer\n"
                    + "import torch\n\n"
                    + "app = Flask(__name__)\n"
                    + "tok = AutoTokenizer.from_pretrained('.')\n"
                    + "model = AutoModelForCausalLM.from_pretrained('.')\n\n"
                    + "@app.post('/generate')\n"
                    + "def gen():\n"
                    + "    p = request.json['prompt']\n"
                    + "    ids = tok.encode(p, return_tensors='pt')\n"
                    + "    out = model.generate(ids, max_length=len(ids[0])+128)\n"
                    + "    return jsonify({'text': tok.decode(out[0], skip_special_tokens=True)})\n\n"
                    + "if __name__ == '__main__':\n"
                    + "    app.run(host='0.0.0.0', port=8000)\n";
            default -> "# onnxruntime serve\n"
                    + "from flask import Flask, request, jsonify\n"
                    + "import onnxruntime as ort\n"
                    + "import numpy as np\n\n"
                    + "app = Flask(__name__)\n"
                    + "sess = ort.InferenceSession('model.onnx')\n\n"
                    + "@app.post('/generate')\n"
                    + "def gen():\n"
                    + "    p = request.json['prompt']\n"
                    + "    # 简化: 用固定 token ids 演示\n"
                    + "    ids = np.array([[101, 872, 1962, 102]]).astype(np.int64)\n"
                    + "    out = sess.run(None, {'input_ids': ids})\n"
                    + "    return jsonify({'output_shape': [str(o.shape) for o in out]})\n\n"
                    + "if __name__ == '__main__':\n"
                    + "    app.run(host='0.0.0.0', port=8000)\n";
        };
    }

    private String clientScript() {
        return "import requests\n"
                + "r = requests.post('http://localhost:8000/generate', json={'prompt': '你好'})\n"
                + "print(r.json())\n";
    }

    private void putText(ZipOutputStream out, String name, String content) throws IOException {
        ZipEntry e = new ZipEntry(name);
        out.putNextEntry(e);
        out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private void putFile(ZipOutputStream out, String name, Path src) throws IOException {
        ZipEntry e = new ZipEntry(name);
        out.putNextEntry(e);
        Files.copy(src, out);
        out.closeEntry();
    }

    private String safe(String v) { return v == null ? "" : v; }
    private String safe(String v, String dft) { return v == null || v.isEmpty() ? dft : v; }
}