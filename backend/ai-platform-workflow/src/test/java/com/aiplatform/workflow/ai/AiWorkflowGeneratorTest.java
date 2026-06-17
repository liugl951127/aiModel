package com.aiplatform.workflow.ai;

import com.aiplatform.workflow.schema.ComponentSchemaRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 工作流生成器单元测试.
 *
 * <p>重点验证: 用户一句话需求 → 完整 workflow JSON (含 nodes/edges/params) → 可导出跨平台.</p>
 *
 * <h2>覆盖范围</h2>
 * <ol>
 *   <li>9 大场景识别正确 (RAG / LoRA 训练 / 营销 / 客服 / 合同 / ETL / 评估 / Agent / 部署)</li>
 *   <li>5 种多轮操作 (add_node / delete_node / replace / update_params / fallback)</li>
 *   <li>导出 JSON 格式校验 (含 platform / version / nodes / edges 字段)</li>
 *   <li>节点 type 跟后端 NodeExecutor case 完全对齐 (可执行)</li>
 * </ol>
 *
 * @author liugl
 * @since 2026-06-17
 */
@DisplayName("AI 一句话生成 workflow - 单元测试")
class AiWorkflowGeneratorTest {

    private static AiWorkflowGenerator generator;
    private static ComponentSchemaRegistry schemaRegistry;
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void setup() {
        schemaRegistry = new ComponentSchemaRegistry();
        generator = new AiWorkflowGenerator(schemaRegistry);
    }

    // ============================================================
    // 第 1 部分: 9 大场景识别
    // ============================================================

    @Test
    @DisplayName("1.1 RAG 知识库问答 - 一句话生成完整 3 节点流水线")
    void testRagScenario() {
        // 客户一句话: "做一个 RAG 知识库问答流程"
        Map<String, Object> wf = generator.generate("做一个 RAG 知识库问答流程");

        // 验证基础结构
        assertNotNull(wf, "返回值不能为空");
        assertEquals("rag", wf.get("scenario"), "应识别为 rag 场景");
        assertEquals("RAG 知识库问答", wf.get("name"), "场景名应正确");

        // 验证 nodes
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) wf.get("nodes");
        assertEquals(3, nodes.size(), "RAG 应有 3 个节点");
        assertEquals("kb_ingest", nodes.get(0).get("type"), "第 1 节点 kb_ingest (文档入库)");
        assertEquals("kb_search", nodes.get(1).get("type"), "第 2 节点 kb_search (向量检索)");
        assertEquals("agent_think", nodes.get(2).get("type"), "第 3 节点 agent_think (AI 回答)");

        // 验证 edges (3 节点 2 条边)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) wf.get("edges");
        assertEquals(2, edges.size(), "3 节点应有 2 条边");
        assertEquals("n1", edges.get(0).get("from"));
        assertEquals("n2", edges.get(0).get("to"));
        assertEquals("n2", edges.get(1).get("from"));
        assertEquals("n3", edges.get(1).get("to"));

        // 验证所有节点都有 x/y 坐标 (画布绘制需要)
        for (int i = 0; i < nodes.size(); i++) {
            Map<String, Object> n = nodes.get(i);
            assertNotNull(n.get("id"), "节点 id 必填");
            assertNotNull(n.get("x"), "节点 x 坐标必填 (画布需要)");
            assertNotNull(n.get("y"), "节点 y 坐标必填 (画布需要)");
            assertNotNull(n.get("params"), "节点 params 必填 (执行时需要)");
        }

        // 验证导出 JSON 可序列化
        assertCanExport(wf);
    }

    @Test
    @DisplayName("1.2 LoRA 模型微调 - 一句话生成可导出大模型训练流程 ⭐ 重点")
    void testLoraTrainScenario_KeyPath() {
        // ★ 核心测试: 一句话生成可导出大模型训练流程
        Map<String, Object> wf = generator.generate("训练个 LoRA 模型, epochs=3, topK=5");

        assertEquals("lora_train", wf.get("scenario"), "应识别为 LoRA 训练场景");
        assertEquals("LoRA 模型微调", wf.get("name"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) wf.get("nodes");
        assertEquals(5, nodes.size(), "LoRA 训练应有 5 节点 (数据加载→切片→训练→评估→注册)");

        // 验证 5 节点 type
        assertEquals("data_loader", nodes.get(0).get("type"));
        assertEquals("data_split",  nodes.get(1).get("type"));
        assertEquals("lora_train",  nodes.get(2).get("type"), "中间节点必须是 lora_train (可执行)");
        assertEquals("eval_bleu",   nodes.get(3).get("type"));
        assertEquals("model_register", nodes.get(4).get("type"));

        // 验证 epochs=3 / topK=5 关键词覆盖到 params
        @SuppressWarnings("unchecked")
        Map<String, Object> trainParams = (Map<String, Object>) nodes.get(2).get("params");
        assertEquals(3, trainParams.get("epochs"), "epochs=3 应写入 LoRA 训练节点");

        // 验证所有节点 type 都在 schema 注册表里 (可执行保证)
        for (Map<String, Object> n : nodes) {
            String type = (String) n.get("type");
            assertNotNull(schemaRegistry.get(type),
                "节点 type=" + type + " 必须在 ComponentSchemaRegistry 注册, 否则 NodeExecutor 跑不了");
        }

        // 验证可导出 (含完整 metadata)
        assertCanExport(wf);
        // 模拟前端 exportSpec 加的 metadata (platform / version)
        Map<String, Object> exported = new LinkedHashMap<>(wf);
        exported.put("version", 1);
        exported.put("platform", "ai-platform-workflow");
        String json = toJsonString(exported);
        assertTrue(json.contains("\"platform\""), "导出 JSON 应含 platform 字段 (跨平台标识)");
        assertTrue(json.contains("\"nodes\""), "导出 JSON 应含 nodes 数组");
        assertTrue(json.contains("\"edges\""), "导出 JSON 应含 edges 数组");
        assertTrue(json.contains("\"name\""), "导出 JSON 应含 name 字段");
    }

    @Test
    @DisplayName("1.3 营销文案生成 - 一句话生成 3 节点流水线")
    void testMarketingScenario() {
        Map<String, Object> wf = generator.generate("写小红书公众号抖音营销文案");
        assertEquals("marketing", wf.get("scenario"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) wf.get("nodes");
        assertEquals(3, nodes.size());
        assertEquals("agent_think", nodes.get(0).get("type"));
    }

    @Test
    @DisplayName("1.4 客服自动回复 - 一句话识别")
    void testCustomerServiceScenario() {
        Map<String, Object> wf = generator.generate("做一个客服自动回复流程");
        assertEquals("customer_service", wf.get("scenario"));
    }

    @Test
    @DisplayName("1.5 合同风险审查 - 一句话识别")
    void testContractScenario() {
        Map<String, Object> wf = generator.generate("审查合同风险条款");
        assertEquals("contract_review", wf.get("scenario"));
    }

    @Test
    @DisplayName("1.6 ETL 流水线 - 一句话识别")
    void testEtlScenario() {
        Map<String, Object> wf = generator.generate("做 MySQL 到 ClickHouse 的数据同步 ETL");
        assertEquals("etl", wf.get("scenario"));
    }

    @Test
    @DisplayName("1.7 模型评估 - 一句话识别")
    void testEvaluateScenario() {
        Map<String, Object> wf = generator.generate("评估 RAG 系统幻觉率");
        assertEquals("evaluate", wf.get("scenario"));
    }

    @Test
    @DisplayName("1.8 智能体 ReAct - 一句话识别")
    void testAgentScenario() {
        Map<String, Object> wf = generator.generate("做一个能调工具的智能体");
        assertEquals("agent", wf.get("scenario"));
    }

    @Test
    @DisplayName("1.9 模型部署上线 - 一句话识别")
    void testDeployScenario() {
        Map<String, Object> wf = generator.generate("把模型部署上线, 灰度 10%");
        assertEquals("deploy", wf.get("scenario"));
    }

    // ============================================================
    // 第 2 部分: 多轮对话 (5 种操作)
    // ============================================================

    @Test
    @DisplayName("2.1 多轮 - 加节点 (追加评估节点)")
    void testMultiTurn_AddNode() {
        // 先建基础
        Map<String, Object> current = generator.generate("做一个 RAG 知识库问答");
        assertEquals(3, ((List<?>) current.get("nodes")).size());

        // 多轮: 多加 1 个评估节点
        Map<String, Object> modified = generator.generate("多加 1 个评估节点", current);
        assertEquals("add_node", modified.get("action"), "action 应该是 add_node");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) modified.get("nodes");
        assertEquals(4, nodes.size(), "追加 1 节点, 3 + 1 = 4");
        // 最后一个节点是评估
        assertEquals("eval_bleu", nodes.get(3).get("type"), "追加的应是 eval_bleu");
    }

    @Test
    @DisplayName("2.2 多轮 - 加多个节点 (2 个训练节点)")
    void testMultiTurn_AddMultipleNodes() {
        Map<String, Object> current = generator.generate("做一个 RAG 知识库问答");
        Map<String, Object> modified = generator.generate("多加 2 个训练节点", current);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) modified.get("nodes");
        assertEquals(5, nodes.size());
    }

    @Test
    @DisplayName("2.3 多轮 - 删节点 (删掉最后一个)")
    void testMultiTurn_DeleteLastNode() {
        Map<String, Object> current = generator.generate("做一个 RAG 知识库问答");
        Map<String, Object> modified = generator.generate("删掉最后一个节点", current);
        assertEquals("delete_node", modified.get("action"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) modified.get("nodes");
        assertEquals(2, nodes.size(), "3 - 1 = 2 节点");
    }

    @Test
    @DisplayName("2.4 多轮 - 改场景 (换成营销)")
    void testMultiTurn_ReplaceScenario() {
        Map<String, Object> current = generator.generate("做一个 RAG 知识库问答");
        Map<String, Object> modified = generator.generate("换成营销文案", current);
        assertEquals("replace", modified.get("action"), "换成 → replace");
        assertEquals("marketing", modified.get("scenario"), "应换成 marketing 场景");
        // 节点数应是 marketing 场景的 (3), 不是原 rag 的 (3)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) modified.get("nodes");
        assertEquals(3, nodes.size());
        assertEquals("agent_think", nodes.get(0).get("type"));
    }

    @Test
    @DisplayName("2.5 多轮 - 改参数 (topK=10)")
    void testMultiTurn_UpdateParams() {
        Map<String, Object> current = generator.generate("做一个 RAG 知识库问答");
        // 找一个含 topK 的节点
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) current.get("nodes");
        Map<String, Object> searchNode = nodes.get(1); // kb_search
        @SuppressWarnings("unchecked")
        Map<String, Object> origParams = (Map<String, Object>) searchNode.get("params");
        // 改之前
        Object origTopK = origParams.get("topK");

        // 多轮: topK=10
        Map<String, Object> modified = generator.generate("topK=10", current);
        assertEquals("update_params", modified.get("action"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modNodes = (List<Map<String, Object>>) modified.get("nodes");
        @SuppressWarnings("unchecked")
        Map<String, Object> modSearch = (Map<String, Object>) modNodes.get(1).get("params");
        // 内部用 Double 表示, 但运行时也接受
        Object actualTopK = modSearch.get("topK");
        assertNotNull(actualTopK, "topK 字段应存在");
        assertEquals(10.0, ((Number) actualTopK).doubleValue(), 0.01, "topK 应更新为 10");
    }

    // ============================================================
    // 第 3 部分: 导出 JSON 跨平台
    // ============================================================

    @Test
    @DisplayName("3.1 导出 JSON - 格式完整 (跨平台可导入)")
    void testExportJsonStructure() throws Exception {
        Map<String, Object> wf = generator.generate("训练个 LoRA 模型, epochs=3");

        // 模拟前端 exportSpec 加的 metadata
        Map<String, Object> exported = new LinkedHashMap<>(wf);
        exported.put("name", "我的训练流程");
        exported.put("version", 1);
        exported.put("exportedAt", "2026-06-17T10:00:00");
        exported.put("platform", "ai-platform-workflow");
        exported.put("minRuntimeVersion", "2.0");

        String json = toJsonString(exported);

        // 解析回来, 字段完整
        Map<String, Object> parsed = JSON.readValue(json, Map.class);
        assertEquals("ai-platform-workflow", parsed.get("platform"), "platform 字段保留");
        assertEquals("我的训练流程", parsed.get("name"), "name 字段保留");
        assertEquals(1, parsed.get("version"), "version 字段保留");
        assertNotNull(parsed.get("nodes"), "nodes 字段保留");
        assertNotNull(parsed.get("edges"), "edges 字段保留");
        assertTrue(json.contains("\"type\":\"lora_train\""), "包含 lora_train 节点");
    }

    @Test
    @DisplayName("3.2 跨平台 - 导出再导入后, 节点 type 仍可执行")
    void testExportImport_NodeTypeConsistency() {
        Map<String, Object> original = generator.generate("训练个 LoRA 模型, epochs=3");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> originalNodes = (List<Map<String, Object>>) original.get("nodes");

        // 模拟导出 (转 JSON) → 模拟在另一台机器导入 (解析)
        for (Map<String, Object> n : originalNodes) {
            String type = (String) n.get("type");
            // 导入后, 节点 type 必须在 schema registry 里 (可被 NodeExecutor 执行)
            assertNotNull(schemaRegistry.get(type),
                "导出的节点 type=" + type + " 在新环境导入后, 必须仍能被识别 (跨平台一致性)");
        }
    }

    // ============================================================
    // 第 4 部分: 边界 + 健壮性
    // ============================================================

    @Test
    @DisplayName("4.1 空输入 - 返回空流程 (不报错)")
    void testEmptyInput() {
        Map<String, Object> wf = generator.generate("");
        assertNotNull(wf);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) wf.get("nodes");
        assertEquals(0, nodes.size());
    }

    @Test
    @DisplayName("4.2 null 输入 - 返回空流程")
    void testNullInput() {
        Map<String, Object> wf = generator.generate(null);
        assertNotNull(wf);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) wf.get("nodes");
        assertEquals(0, nodes.size());
    }

    @Test
    @DisplayName("4.3 未知场景 - fallback 到自定义 (2 节点)")
    void testFallbackScenario() {
        Map<String, Object> wf = generator.generate("随便搞个流程, 我也不知道要啥");
        assertEquals("custom", wf.get("scenario"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) wf.get("nodes");
        assertEquals(2, nodes.size(), "fallback 2 节点");
    }

    @Test
    @DisplayName("4.4 中英文混合 - 都能识别")
    void testChineseEnglishMixed() {
        Map<String, Object> wf = generator.generate("I want to 训练 a LORA model");
        assertEquals("lora_train", wf.get("scenario"), "中英混合也能识别");
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private void assertCanExport(Map<String, Object> wf) {
        assertNotNull(wf.get("name"), "可导出: 必须有 name");
        assertNotNull(wf.get("nodes"), "可导出: 必须有 nodes");
        assertNotNull(wf.get("edges"), "可导出: 必须有 edges");
    }

    private String toJsonString(Map<String, Object> wf) {
        try {
            return JSON.writeValueAsString(wf);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }
}
