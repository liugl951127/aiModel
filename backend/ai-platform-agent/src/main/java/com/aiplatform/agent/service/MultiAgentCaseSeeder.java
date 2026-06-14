package com.aiplatform.agent.service;

import com.aiplatform.agent.entity.MultiAgentCase;
import com.aiplatform.common.tenant.TenantContext;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * 多智能体案例种子数据。
 * <p>应用启动时把演示案例写入 {@code agent_multi_agent_case} 表（upsert，
 * 可重复启动）。这些案例既可作为模板复用，也是项目成果展示页的数据源。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MultiAgentCaseSeeder {

    private final MultiAgentCaseService service;
    private final javax.sql.DataSource dataSource;

    /**
     * Spring 启动后立即执行种子写入。
     */
    @Bean
    @Order(1)
    public ApplicationRunner seedMultiAgentCasesRunner() {
        return args -> {
            log.info("[CASE-SEED] seeding multi-agent demo cases...");
            // 多租户拦截器在所有 mapper 调用前加 tenant_id 谓词；这里用 default tenant=0
            TenantContext.setTenantId(0L);
            try {
                ensureTable();
                for (MultiAgentCase c : seedCases()) {
                    service.upsert(c);
                }
                log.info("[CASE-SEED] done. cases={}", service.listAll().size());
            } catch (Throwable t) {
                // 不让数据库不可用阻断应用启动——生产部署 DB 可用后重启即重入
                log.warn("[CASE-SEED] skipped (db unavailable): {}", t.getMessage());
            } finally {
                TenantContext.clear();
            }
        };
    }

    /**
     * 启动时检查 case 表是否存在（idempotent DDL），不存在就创建。
     * 这样 H2 / MySQL / Postgres 都能直接跑。
     */
    private void ensureTable() throws java.sql.SQLException {
        try (var con = dataSource.getConnection();
             var st = con.createStatement()) {
            // MySQL / H2-MySQL-mode 兼容语法
            st.execute("CREATE TABLE IF NOT EXISTS agent_multi_agent_case ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "case_key VARCHAR(128) NOT NULL UNIQUE,"
                    + "title VARCHAR(255) NOT NULL,"
                    + "summary VARCHAR(1024) NOT NULL DEFAULT '',"
                    + "description LONGTEXT,"
                    + "domain VARCHAR(64) NOT NULL DEFAULT 'general',"
                    + "agent_spec LONGTEXT NOT NULL,"
                    + "flow_spec LONGTEXT NOT NULL,"
                    + "final_output LONGTEXT,"
                    + "kpis LONGTEXT,"
                    + "featured INT NOT NULL DEFAULT 0,"
                    + "tenant_id BIGINT NOT NULL DEFAULT 0,"
                    + "create_by VARCHAR(64),"
                    + "update_by VARCHAR(64),"
                    + "create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "deleted INT NOT NULL DEFAULT 0"
                    + ")");
        }
    }

    /**
     * 内置案例集合。
     */
    private List<MultiAgentCase> seedCases() {
        return List.of(buildLaunchCampaignCase(), buildResearchReportCase());
    }

    /* ====================== 案例 1 ====================== */

    /**
     * 案例 1：AI 产品发布营销 → 法务审查 → 知识库沉淀 → 训练数据生成
     */
    private MultiAgentCase buildLaunchCampaignCase() {
        JSONObject c = new JSONObject();

        c.put("caseKey", "launch-campaign-2025");
        c.put("title", "AI 产品发布：营销 → 法务 → 知识沉淀 → 训练数据");
        c.put("summary",
                "一个真实业务场景的多智能体编排案例：用户提出产品发布需求，"
              + "4 个智能体（营销 / 法务 / 知识 / 训练）按流程协作，输出营销文案、"
              + "知识库文档和训练 Q/A 集。");
        c.put("description",
                "展示 AI Agent Platform 完整的多智能体协作能力：每个智能体有独立"
              + "的 prompt 角色、可调用的工具（联网搜索 / 知识库 / 文件服务器 / 训练器），"
              + "且流程用 DAG 描述，节点间通过上下文共享结果。");
        c.put("domain", "marketing");

        // === agentSpec: 4 个 agent + 工具 ===
        JSONArray agents = new JSONArray();
        agents.add(agent("marketing_agent", "营销 Agent", "search|kbs|file|llm",
                "负责联网搜集竞品资料、生成营销文案、产出 FAQ"));
        agents.add(agent("legal_agent", "法务 Agent", "kbs|llm",
                "扫描营销文案里的违规风险点（医疗承诺 / 极限词 / 数据来源缺失），"
              + "返回 PASS / REVISE / BLOCK 决策"));
        agents.add(agent("knowledge_agent", "知识库 Agent", "kbs|file|llm",
                "把审过的资料结构化后写入知识库（kb=kb-product）"));
        agents.add(agent("training_agent", "训练 Agent", "kbs|trainer|llm",
                "从知识库文档自动生成 Q/A 训练对，提交到 trainer 服务训练"));

        // === flowSpec: workflow DAG ===
        JSONArray steps = new JSONArray();
        steps.add(step("research", "web_search + kbs", List.of(),
                Map.of("query", "AI 助手 2025 营销文案案例", "kbId", 1)));
        steps.add(step("draft", "marketing_agent", List.of("research"),
                Map.of("prompt", "基于上述资料写一份 500 字中文发布文案 + 5 条标题候选")));
        steps.add(step("legal_review", "legal_agent", List.of("draft"),
                Map.of("rubric", "中国广告法 + 平台内容规范")));
        steps.add(step("publish", "knowledge_agent", List.of("legal_review"),
                Map.of("kbId", 1, "tag", "launch-2025")));
        steps.add(step("gen_qa", "training_agent", List.of("publish"),
                Map.of("kbId", 1, "topics", List.of("产品功能", "价格策略", "使用方法"))));
        steps.add(step("train", "trainer.submit", List.of("gen_qa"),
                Map.of("maxIters", 200, "guard", "strict")));

        // === finalOutput: 完整产出 ===
        JSONObject output = new JSONObject();
        output.put("marketing_copy",
                "## 标题候选\n1. 让 AI 成为你的第二大脑\n2. 1 分钟上手，让工作流起飞\n"
              + "3. 不止是聊天，更是你的协作者\n\n## 正文\n（500 字）……");
        output.put("legal_decision", "PASS");
        output.put("legal_notes", "已删除 1 处'最强'极限词，建议改用'领先'");
        output.put("knowledge_doc_id", 1024);
        output.put("qa_pairs", 36);
        output.put("training_job_id", "java-7a3c");
        output.put("bundle", "/opt/ai-platform/inference-bundles/java-7a3c/");

        // === kpis ===
        JSONObject kpis = new JSONObject();
        kpis.put("duration_sec", 47);
        kpis.put("tokens_generated", 1842);
        kpis.put("tool_calls", 6);
        kpis.put("legal_rewrites", 1);
        kpis.put("final_loss", 3.42);
        kpis.put("hallucination_rejected", 0);
        kpis.put("user_satisfaction", 0.92);

        MultiAgentCase row = new MultiAgentCase();
        row.setTenantId(0L);
        row.setCaseKey(c.getString("caseKey"));
        row.setTitle(c.getString("title"));
        row.setSummary(c.getString("summary"));
        row.setDescription(c.getString("description"));
        row.setDomain(c.getString("domain"));
        row.setAgentSpec(agents.toJSONString());
        row.setFlowSpec(steps.toJSONString());
        row.setFinalOutput(output.toJSONString());
        row.setKpis(kpis.toJSONString());
        row.setFeatured(1);
        return row;
    }

    /* ====================== 案例 2 ====================== */

    /**
     * 案例 2：技术调研报告自动生成（深度研究多智能体）
     */
    private MultiAgentCase buildResearchReportCase() {
        JSONObject c = new JSONObject();
        c.put("caseKey", "deep-research-llm-2026");
        c.put("title", "深度研究：2026 LLM 趋势调研报告自动生成");
        c.put("summary",
                "5 个智能体协作：研究规划 → 文献搜集 → 内容分析 → 报告写作 → 审校。"
              + "展示多智能体在长程任务中的工具协调和上下文共享。");
        c.put("description",
                "完整跑通 Plan-Act-Observe 循环，每个 agent 都可以调用 web_search、"
              + "knowledge_search、file_server 等平台工具，最终输出一份带引用"
              + "和置信度的调研报告。");
        c.put("domain", "research");

        JSONArray agents = new JSONArray();
        agents.add(agent("planner", "规划 Agent", "llm",
                "把宏观研究问题拆分成 3-5 个子问题"));
        agents.add(agent("searcher", "检索 Agent", "web_search|kbs",
                "对每个子问题搜集权威来源"));
        agents.add(agent("analyst", "分析 Agent", "llm|kbs",
                "对搜集到的内容做事实抽取、对比、归因"));
        agents.add(agent("writer", "写作 Agent", "llm|file",
                "把分析结果组织成结构化报告"));
        agents.add(agent("reviewer", "审校 Agent", "llm|kbs",
                "检查引用完整性、数据准确性、合规性"));

        JSONArray steps = new JSONArray();
        steps.add(step("plan", "planner", List.of(),
                Map.of("topic", "2026 LLM 趋势")));
        steps.add(step("search", "searcher", List.of("plan"),
                Map.of("subtopics", List.of("scaling", "alignment", "agents"))));
        steps.add(step("analyze", "analyst", List.of("search"),
                Map.of()));
        steps.add(step("write", "writer", List.of("analyze"),
                Map.of("format", "markdown", "min_words", 3000)));
        steps.add(step("review", "reviewer", List.of("write"),
                Map.of("checks", List.of("citations", "facts", "tone"))));

        JSONObject output = new JSONObject();
        output.put("report_url", "/files/research/2026-llm-trends.md");
        output.put("citations", 18);
        output.put("confidence_avg", 0.84);
        output.put("review_decision", "APPROVED");

        JSONObject kpis = new JSONObject();
        kpis.put("duration_sec", 312);
        kpis.put("web_searches", 12);
        kpis.put("kb_hits", 23);
        kpis.put("review_rewrites", 2);
        kpis.put("user_satisfaction", 0.95);

        MultiAgentCase row = new MultiAgentCase();
        row.setTenantId(0L);
        row.setCaseKey(c.getString("caseKey"));
        row.setTitle(c.getString("title"));
        row.setSummary(c.getString("summary"));
        row.setDescription(c.getString("description"));
        row.setDomain(c.getString("domain"));
        row.setAgentSpec(agents.toJSONString());
        row.setFlowSpec(steps.toJSONString());
        row.setFinalOutput(output.toJSONString());
        row.setKpis(kpis.toJSONString());
        row.setFeatured(1);
        return row;
    }

    /* ---------------- helpers ---------------- */

    private static JSONObject agent(String key, String name, String tools, String persona) {
        JSONObject a = new JSONObject();
        a.put("key", key);
        a.put("name", name);
        a.put("tools", tools);
        a.put("persona", persona);
        return a;
    }

    private static JSONObject step(String name, String handler, List<String> deps, Object params) {
        JSONObject s = new JSONObject();
        s.put("name", name);
        s.put("handler", handler);
        s.put("dependsOn", deps);
        s.put("params", params);
        return s;
    }

    /**
     * 轻量 varargs Map.of helper（支持任意 value 类型，便于 List/嵌套对象）。
     */
    private static final class Map {
        static java.util.Map<String, Object> of(Object... kv) {
            java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
            for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
            return m;
        }
    }
}
