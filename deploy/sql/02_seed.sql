-- Seed data
USE ai_platform;

-- Default tenant
INSERT INTO sys_tenant (id, tenant_code, tenant_name, contact_name, contact_email, status, plan_code, max_users)
VALUES (1, 'default', '默认租户', 'Admin', 'admin@example.com', 1, 'enterprise', 999);

-- Default admin user (password: admin123, BCrypt encoded)
INSERT INTO sys_user (id, tenant_id, username, password, nickname, email, status)
VALUES (1, 1, 'admin', '$2a$10$N8u6WqNQb3FgxT0vbgM5OOqPjtNGrV3vO/k2Gzj4vF1E6V6ZqJfpa', '管理员', 'admin@example.com', 1);

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
