-- Seed data
USE ai_platform;

-- Default tenant
INSERT INTO sys_tenant (id, tenant_code, tenant_name, contact_name, contact_email, status, plan_code, max_users)
VALUES (1, 'default', '默认租户', 'Admin', 'admin@example.com', 1, 'enterprise', 999);

-- Default admin user (password: admin123, BCrypt encoded, $2a$ 10 rounds)
-- is_super_admin=1: 超级管理员，拥有所有租户权限
INSERT INTO sys_user (id, tenant_id, username, password, nickname, email, status, department, is_super_admin)
VALUES (1, 1, 'admin', '$2a$10$4tMHnM6bsrADgZJyK3vI5.z99DvtP6xhQoPAjuayBmGvtdj4Z8zeO', '管理员', 'admin@example.com', 1, '技术部', 1);

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

-- 示例用户（password: demo123 BCrypt 编码）
INSERT INTO sys_user (id, tenant_id, username, password, nickname, email, status, department) VALUES
    (2, 1, 'demo',    '$2a$10$lv5zH33opqzLp4PadeWIseuOGUzRs6Rj6O48PiV/SfKisSMFT0r.y', '演示账号', 'demo@example.com',   1, '市场部'),
    (3, 2, 'manager', '$2a$10$lv5zH33opqzLp4PadeWIseuOGUzRs6Rj6O48PiV/SfKisSMFT0r.y', '王经理',   'manager@example.com', 1, '运营部');

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
