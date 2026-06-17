-- Seed data
USE ai_platform;

-- Default tenant
INSERT INTO sys_tenant (id, tenant_code, tenant_name, contact_name, contact_email, status, plan_code, max_users)
VALUES (1, 'default', '默认租户', 'Admin', 'admin@example.com', 1, 'enterprise', 999);

-- Default admin user (password: admin123, BCrypt encoded, $2a$ 10 rounds)
-- is_super_admin=1: 超级管理员，拥有所有租户权限
-- hash 由 ai-platform-user/src/main/java/com/aiplatform/user/util/BCryptHashMain.java
-- 用 Spring BCryptPasswordEncoder 生成 (跟 AuthService 用的同款)
INSERT INTO sys_user (id, tenant_id, username, password, nickname, email, status, department, is_super_admin)
VALUES (1, 1, 'admin', '$2a$10$4Wv4SlzLLYBNG7Y88Kcmq.FpJb58h7pcMExwGlFDY.Cw4eznHUIbe', '管理员', 'admin@example.com', 1, '技术部', 1);

-- Default agent
INSERT INTO agent_agent (id, tenant_id, agent_code, agent_name, agent_type, description, system_prompt, tools, model_code, temperature, max_steps, status)
VALUES (1, 1, 'A-DEFAULT01', '通用助手', 'react', '平台默认智能体',
        '你是一个乐于助人的智能助手。请尽可能用中文回答，并在不确定时使用工具。',
        'calculator,current_time,knowledge_search', 'default', 0.7, 5, 1);

-- Sample knowledge base
INSERT INTO kb_base (id, tenant_id, kb_code, kb_name, description, index_name, status, embedding_model)
VALUES (1, 1, 'KB-DEFAULT', '默认知识库', '平台使用手册与常见问题', 'kb-default', 1, 'byte-hash');

-- Sample role
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, status)
VALUES (1, 1, 'PLATFORM_ADMIN', '平台管理员', '系统超级管理员', 1);


-- 示例公司（可由用户自由切换体验）
INSERT INTO sys_tenant (id, tenant_code, tenant_name, contact_name, contact_email, status, plan_code, max_users) VALUES
    (2, 'demo-corp',   '示例科技公司',  '王经理', 'demo@example.com',   1, 'enterprise', 50),
    (3, 'startup-co',  '创业小公司',    '李总',   'startup@example.com', 1, 'free', 5);

-- 示例用户（password: demo123 BCrypt 编码，hash 由 Spring BCryptPasswordEncoder 生成）
INSERT INTO sys_user (id, tenant_id, username, password, nickname, email, status, department) VALUES
    (2, 1, 'demo',    '$2a$10$UYseHeqvN83UyTBO9uQMZe0qXwYUObswQlje42BQ2Hjdg9JfhhUIy', '演示账号', 'demo@example.com',   1, '市场部'),
    (3, 2, 'manager', '$2a$10$UYseHeqvN83UyTBO9uQMZe0qXwYUObswQlje42BQ2Hjdg9JfhhUIy', '王经理',   'manager@example.com', 1, '运营部');

-- 用户-公司 关联
-- admin: 属于 3 家公司, default 为默认
-- demo: 属于默认公司, 是 member
-- manager: 属于示例科技公司, 是 owner
INSERT INTO sys_user_tenant (id, user_id, tenant_id, role_in_tenant, is_default) VALUES
    (1, 1, 1, 'owner', 1),
    (2, 1, 2, 'admin', 0),
    (3, 1, 3, 'admin', 0),
    (4, 2, 1, 'member', 1),
    (5, 3, 2, 'owner', 1);

-- 用户-角色 关联
-- admin: PLATFORM_ADMIN (超管) + user
-- demo/manager: user
INSERT INTO sys_user_role (id, user_id, role_id, tenant_id) VALUES
    (1, 1, 1, 1),
    (2, 2, 1, 1),
    (3, 3, 1, 2);

-- ====================================================
-- 业务表 seed 数据
-- ====================================================

INSERT INTO biz_customer (id, tenant_id, name, industry, scale, contact_name, contact_phone, contact_email, level, owner_user_id, notes) VALUES
(1, 1, '阿里云', '云计算', '超', '张无忌', '13800001111', 'zhang@aliyun.com', 'S', 1, '战略客户, 已签约 3 单'),
(2, 1, '腾讯科技', '互联网', '超', '赵敏', '13800002222', 'zhao@tencent.com', 'S', 1, '续约中'),
(3, 1, '字节跳动', '互联网', '超', '周芷若', '13800003333', 'zhou@bytedance.com', 'A', 1, '重点跟进'),
(4, 1, '美团', '本地生活', '大', '小昭', '13800004444', 'xz@meituan.com', 'B', 1, '试用阶段'),
(5, 1, '京东', '电商', '大', '阿离', '13800005555', 'ali@jd.com', 'A', 1, '洽谈中');

INSERT INTO biz_chat (customer_id, owner_user_id, subject, type, status, next_step, next_date, summary) VALUES
(1, 1, 'Q2 续约', '面谈', '已成交', '等待合同签署', '2026-06-20 14:00', '客户对价格满意, 同意续约 12 个月'),
(2, 1, '云服务扩容', '电话', '进行中', '发送方案', '2026-06-18 10:00', '客户业务增长, 需要扩容'),
(3, 1, '新产品试用', '微信', '进行中', '安排 demo', '2026-06-19 15:00', '客户对新功能感兴趣'),
(4, 1, '技术对接', '邮件', '进行中', '发送技术文档', '2026-06-21 09:00', '技术团队需对接 API'),
(5, 1, '商务洽谈', '面谈', '已搁置', '后续跟进', '2026-07-01 10:00', '客户预算不足, 暂缓');

INSERT INTO biz_opportunity (customer_id, name, amount, stage, probability, expected_date, owner_user_id) VALUES
(1, '阿里云 Q2 续约', 1200000.00, '成交', 95, '2026-06-30', 1),
(2, '腾讯云扩容', 800000.00, '谈判', 70, '2026-07-15', 1),
(3, '字节 AI 模型', 1500000.00, '方案', 50, '2026-08-30', 1),
(4, '美团智能客服', 300000.00, '接触', 30, '2026-09-30', 1),
(5, '京东企业版', 2000000.00, '线索', 10, '2026-12-31', 1);

INSERT INTO biz_quote (id, customer_id, opportunity_id, code, title, total_amount, discount, final_amount, status) VALUES
(1, 1, 1, 'Q-2026-0001', '阿里云 Q2 续约报价', 1200000.00, 0.00, 1200000.00, '已接受'),
(2, 2, 2, 'Q-2026-0002', '腾讯云扩容', 800000.00, 0.05, 760000.00, '已发送'),
(3, 3, 3, 'Q-2026-0003', '字节 AI 模型', 1500000.00, 0.00, 1500000.00, '审批中');

INSERT INTO biz_contract (id, customer_id, opportunity_id, quote_id, code, title, amount, sign_date, start_date, end_date, status) VALUES
(1, 1, 1, 1, 'C-2026-0001', '阿里云年度服务合同', 1200000.00, '2026-06-15', '2026-07-01', '2027-06-30', '执行中');

INSERT INTO biz_order (id, customer_id, contract_id, code, amount, paid, status) VALUES
(1, 1, 1, 'O-2026-0001', 1200000.00, 600000.00, '部分付款');

INSERT INTO biz_payment (order_id, amount, method, status, paid_at) VALUES
(1, 600000.00, '银行转账', '已收款', '2026-06-15 10:00:00');

INSERT INTO biz_product (code, name, category, price, unit, description, stock) VALUES
('P-AI-INFER', 'AI 推理 API', 'AI 服务', 0.01, '次', '按调用次数计费', 99999),
('P-AI-TRAIN', 'AI 模型训练', 'AI 服务', 5000.00, '次', '单次训练任务', 99),
('P-KB', '知识库服务', '数据服务', 1000.00, '月', '100 万文档', 50),
('P-WORKFLOW', '工作流引擎', '自动化', 2000.00, '月', '无限工作流', 30);

INSERT INTO biz_service (code, name, category, price, description, sla_hours) VALUES
('S-IMPL', '实施服务', '咨询', 50000.00, '现场实施, 含培训', 72),
('S-CONSULT', '专家咨询', '咨询', 5000.00, '1 对 1 专家, 按天', 24),
('S-SUPPORT', '技术支持', '运维', 10000.00, '7x24 优先支持, 按月', 4);

INSERT INTO biz_expense (order_id, category, amount, happened_at, notes, created_by) VALUES
(1, '差旅', 5000.00, '2026-06-10 10:00:00', '北京客户拜访', 1),
(1, '招待', 3000.00, '2026-06-12 19:00:00', '客户晚宴', 1);

-- ====================================================
-- Seata 演示种子 (user_credits: 给 admin 充 100 万 token 启动即用)
-- ====================================================
INSERT INTO user_credits (user_id, username, credits, consumed) VALUES
(1, 'admin', 1000000, 0),
(2, 'liugl', 500000, 0),
(3, 'demo', 100000, 0);

-- 多智能体案例库 (3 个示例, 首页推荐)
INSERT INTO agent_multi_agent_case (case_key, title, summary, description, domain, agent_spec, flow_spec, final_output, kpis, featured) VALUES
('rag-qa-demo', '企业知识库问答', '基于 RAG 的内部知识库智能问答系统',
 '员工上传 PDF 文档, 系统自动切片向量化, AI 回答时引用原文, 1 秒返回',
 'knowledge', '{"agents":[{"name":"Retriever","tools":["kb_search"]},{"name":"Responder","tools":[]}]}',
 '[{"step":"ingest","tool":"kb_ingest"},{"step":"search","tool":"kb_search"},{"step":"respond","tool":"agent_think"}]',
 '{"accuracy":0.92,"avg_latency_ms":850}',
 '{"accuracy":0.92,"latency_ms":850,"refusal_rate":0.05}', 1),
('marketing-copy', '营销文案生成', '从产品白皮书一键生成小红书/公众号/抖音 3 平台文案',
 '输入产品名+卖点, AI 自动写 3 套不同风格文案',
 'marketing', '{"agents":[{"name":"CopyWriter","tools":["llm_call"]}]}',
 '[{"step":"plan","tool":"agent_think"},{"step":"write","tool":"llm_call"}]',
 '{"variants":3,"tokens_avg":280}',
 '{"engagement_lift":0.45,"cost_per_copy":0.02}', 0),
('legal-review', '合同风险审查', '上传合同 PDF, AI 自动标出风险条款并给出修改建议',
 '支持中英双语, 命中 50+ 风险模式 (违约金/排他/单方解除等)',
 'legal', '{"agents":[{"name":"LegalExpert","tools":["llm_call","regex_match"]}]}',
 '[{"step":"extract","tool":"pdf_parse"},{"step":"scan","tool":"regex_match"},{"step":"explain","tool":"agent_think"}]',
 '{"risk_clauses":12,"suggestions":18}',
 '{"accuracy":0.89,"false_positive":0.07}', 0);
