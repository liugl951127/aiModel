-- =============================================================================
--  AI Agent Platform - 完整数据库初始化脚本 (一文件版)
--  Version: 2.0  (2026-06-17)
--  Lines: ~800  |  Tables: 32  |  Lib: ai_platform
--
--  本文件包含本项目需要的全部 32 张业务表 + 种子数据.
--  Nacos / Seata 库不在此文件内 (官方 schema 动态变化, 见末尾说明).
--
--  一键跑 (PowerShell):
--    mysql -uroot -p951127 < deploy\sql\00_init_all.sql
--
--  跑完后:
--    use ai_platform;
--    show tables;       -- 32 张
--    select * from sys_user;  -- 3 个种子用户 (admin/liugl/demo)
--    select * from biz_customer;  -- 业务客户种子
--
--  重新跑安全: 所有表先 DROP 再 CREATE, 不会污染
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
--  SECTION 1: 32 张表 DDL
-- =============================================================================

-- ai_platform 库
CREATE DATABASE IF NOT EXISTS ai_platform DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_platform;


CREATE DATABASE IF NOT EXISTS ai_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_platform;

-- ---------------------------------------------------------------------------
-- Tenant / User / Role / Menu
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS sys_tenant;
CREATE TABLE sys_tenant (
    id              BIGINT       NOT NULL COMMENT '雪花主键',
    tenant_code     VARCHAR(64)  NOT NULL,
    tenant_name     VARCHAR(128) NOT NULL,
    contact_name    VARCHAR(64),
    contact_phone   VARCHAR(32),
    contact_email   VARCHAR(128),
    status          INT          DEFAULT 1,
    expire_time     DATETIME,
    plan_code       VARCHAR(32)  DEFAULT 'free',
    max_users       INT          DEFAULT 5,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户';

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    username        VARCHAR(64)  NOT NULL,
    password        VARCHAR(128) NOT NULL,
    nickname        VARCHAR(64),
    email           VARCHAR(128),
    phone           VARCHAR(32),
    avatar          VARCHAR(255),
    department      VARCHAR(64)  COMMENT '部门（员工所属部门，如"研发部"/"市场部"）',
    is_super_admin  TINYINT      DEFAULT 0  COMMENT '是否超管 (1=超管，拥有所有租户权限)。 AuthService 默认 username=admin 判定，业务可改读本字段。',
    status          INT          DEFAULT 1,
    last_login_ip   VARCHAR(64),
    last_login_time DATETIME,
    create_by       BIGINT,
    update_by       BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username, tenant_id),
    KEY idx_department (department),
    KEY idx_is_super (is_super_admin)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户';

DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    role_code       VARCHAR(64)  NOT NULL,
    role_name       VARCHAR(128) NOT NULL,
    description     VARCHAR(255),
    status          INT          DEFAULT 1,
    create_by       BIGINT,
    update_by       BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色';

-- 用户-公司 多对多关联 (一个用户可属于多家公司 / 一家公司可有多个用户)
DROP TABLE IF EXISTS sys_user_tenant;
CREATE TABLE sys_user_tenant (
    id              BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL COMMENT 'sys_user.id',
    tenant_id       BIGINT       NOT NULL COMMENT 'sys_tenant.id',
    role_in_tenant  VARCHAR(32)  DEFAULT 'member' COMMENT '在该公司的角色: owner/admin/member/guest',
    is_default      TINYINT      DEFAULT 0 COMMENT '是否默认公司 (1=是, 0=否)',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_tenant (user_id, tenant_id),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-公司 关联';

-- 用户-角色 多对多关联
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    id              BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    role_id         BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1 COMMENT '角色在该租户下有效',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role_tenant (user_id, role_id, tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-角色 关联';

-- 角色-菜单 多对多关联
DROP TABLE IF EXISTS sys_role_menu;
CREATE TABLE sys_role_menu (
    id              BIGINT       NOT NULL,
    role_id         BIGINT       NOT NULL,
    menu_id         BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_menu (role_id, menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-菜单 关联';

-- 登录审计 (记录谁/什么时间/什么 IP 登录过, 用于安全审计)
DROP TABLE IF EXISTS sys_login_audit;
CREATE TABLE sys_login_audit (
    id              BIGINT       NOT NULL,
    username        VARCHAR(64)  NOT NULL,
    tenant_id       BIGINT,
    user_id         BIGINT,
    login_ip        VARCHAR(64),
    user_agent      VARCHAR(255),
    login_status    VARCHAR(16)  COMMENT 'SUCCESS / FAILED / LOCKED',
    fail_reason     VARCHAR(255),
    login_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_username (username),
    KEY idx_time (login_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录审计';

DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    parent_id       BIGINT       DEFAULT 0,
    menu_name       VARCHAR(64)  NOT NULL,
    path            VARCHAR(255),
    component       VARCHAR(255),
    icon            VARCHAR(64),
    menu_type       INT          DEFAULT 1,
    permission      VARCHAR(128),
    sort_order      INT          DEFAULT 0,
    visible         INT          DEFAULT 1,
    status          INT          DEFAULT 1,
    create_by       BIGINT,
    update_by       BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜单';

-- ---------------------------------------------------------------------------
-- Model registry / dataset / train job
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS model_registry;
CREATE TABLE model_registry (
    id                   BIGINT       NOT NULL,
    tenant_id            BIGINT       NOT NULL DEFAULT 1,
    model_code           VARCHAR(64)  NOT NULL,
    model_name           VARCHAR(128) NOT NULL,
    model_type           VARCHAR(32)  DEFAULT 'llm',
    base_model           VARCHAR(64),
    description          TEXT,
    tags                 VARCHAR(255),
    framework            VARCHAR(32),
    parameter_count      BIGINT,
    context_length       VARCHAR(32),
    language             VARCHAR(16),
    status               VARCHAR(32)  DEFAULT 'draft',
    version              VARCHAR(32),
    storage_path         VARCHAR(255),
    export_format        VARCHAR(32),
    onnx_path            VARCHAR(255),
    tokenizer_path       VARCHAR(255),
    training_started_at  DATETIME,
    training_finished_at DATETIME,
    metrics              TEXT,
    create_by            BIGINT,
    update_by            BIGINT,
    create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted              INT          DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_code_version (model_code, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '模型注册';

DROP TABLE IF EXISTS model_dataset;
CREATE TABLE model_dataset (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    dataset_code    VARCHAR(64)  NOT NULL,
    dataset_name    VARCHAR(128) NOT NULL,
    format          VARCHAR(32),
    sample_count    BIGINT,
    language        VARCHAR(16),
    description     TEXT,
    storage_path    VARCHAR(255),
    status          VARCHAR(32)  DEFAULT 'active',
    create_by       BIGINT,
    update_by       BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据集';

DROP TABLE IF EXISTS model_train_job;
CREATE TABLE model_train_job (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    job_code        VARCHAR(64)  NOT NULL,
    model_id        BIGINT,
    dataset_id      BIGINT,
    algorithm       VARCHAR(64),
    epochs          INT,
    batch_size      INT,
    learning_rate   DOUBLE,
    status          VARCHAR(32),
    config          TEXT,
    log_path        VARCHAR(255),
    output_path     VARCHAR(255),
    metrics         TEXT,
    started_at      DATETIME,
    finished_at     DATETIME,
    progress        INT          DEFAULT 0,
    create_by       BIGINT,
    update_by       BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '训练任务';

-- ---------------------------------------------------------------------------
-- Agent / Tool / Conversation
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS agent_agent;
CREATE TABLE agent_agent (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    agent_code      VARCHAR(64)  NOT NULL,
    agent_name      VARCHAR(128) NOT NULL,
    agent_type      VARCHAR(32)  DEFAULT 'react',
    description     TEXT,
    avatar          VARCHAR(255),
    system_prompt   TEXT,
    tools           VARCHAR(512),
    model_id        BIGINT,
    model_code      VARCHAR(64),
    temperature     DOUBLE,
    max_steps       INT,
    status          INT          DEFAULT 1,
    tags            VARCHAR(255),
    create_by       BIGINT,
    update_by       BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '智能体';

DROP TABLE IF EXISTS agent_tool;
CREATE TABLE agent_tool (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    tool_code       VARCHAR(64)  NOT NULL,
    tool_name       VARCHAR(128) NOT NULL,
    tool_type       VARCHAR(32),
    description     TEXT,
    parameters      TEXT,
    endpoint        VARCHAR(255),
    handler         VARCHAR(255),
    status          INT          DEFAULT 1,
    create_by       BIGINT,
    update_by       BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工具';

DROP TABLE IF EXISTS agent_conversation;
CREATE TABLE agent_conversation (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    session_id      VARCHAR(64)  NOT NULL,
    agent_id        BIGINT,
    title           VARCHAR(255),
    summary         TEXT,
    status          INT          DEFAULT 1,
    create_by       BIGINT,
    update_by       BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会话';

DROP TABLE IF EXISTS agent_message;
CREATE TABLE agent_message (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    session_id      VARCHAR(64)  NOT NULL,
    role            VARCHAR(16),
    content         LONGTEXT,
    tool_name       VARCHAR(64),
    tool_call       TEXT,
    step            INT,
    status          INT          DEFAULT 1,
    create_by       BIGINT,
    update_by       BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '消息';

-- ---------------------------------------------------------------------------
-- Knowledge base
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS kb_base;
CREATE TABLE kb_base (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    kb_code         VARCHAR(64)  NOT NULL,
    kb_name         VARCHAR(128) NOT NULL,
    description     TEXT,
    index_name      VARCHAR(128),
    status          INT          DEFAULT 1,
    embedding_model VARCHAR(64),
    create_by       BIGINT,
    update_by       BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库';

DROP TABLE IF EXISTS kb_document;
CREATE TABLE kb_document (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    kb_id           BIGINT,
    doc_code        VARCHAR(64),
    doc_name        VARCHAR(255),
    doc_type        VARCHAR(64),
    size_bytes      BIGINT,
    storage_path    VARCHAR(255),
    status          INT          DEFAULT 1,
    chunk_count     INT          DEFAULT 0,
    error_message   TEXT,
    create_by       BIGINT,
    update_by       BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_kb (kb_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库文档';

-- 业务表 (洽谈/商机/合同/报价/订单/费用/产品/服务)

CREATE TABLE IF NOT EXISTS biz_customer (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL DEFAULT 1,
  name            VARCHAR(128) NOT NULL,
  industry        VARCHAR(64),
  scale           VARCHAR(32),                  -- 小/中/大/超
  contact_name    VARCHAR(64),
  contact_phone   VARCHAR(32),
  contact_email   VARCHAR(128),
  address         VARCHAR(256),
  source          VARCHAR(32),                  -- 官网/活动/推荐/广告
  level           VARCHAR(16) DEFAULT 'C',     -- S/A/B/C
  status          INT DEFAULT 1,               -- 1=正常 0=停用
  owner_user_id   BIGINT,
  notes           TEXT,
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id),
  INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户表';

CREATE TABLE IF NOT EXISTS biz_chat (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL DEFAULT 1,
  customer_id     BIGINT NOT NULL,
  owner_user_id   BIGINT,
  subject         VARCHAR(256),
  type            VARCHAR(16),                  -- 面谈/电话/微信/邮件
  status          VARCHAR(16) DEFAULT '进行中',  -- 进行中/已成交/已搁置
  next_step       VARCHAR(256),
  next_date       DATETIME,
  summary         TEXT,
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_customer (customer_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='洽谈记录';

CREATE TABLE IF NOT EXISTS biz_opportunity (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL DEFAULT 1,
  customer_id     BIGINT NOT NULL,
  name            VARCHAR(256) NOT NULL,
  amount          DECIMAL(15,2) DEFAULT 0,
  currency        VARCHAR(8) DEFAULT 'CNY',
  stage           VARCHAR(16) DEFAULT '线索',   -- 线索/接触/方案/谈判/成交/输单
  probability     INT DEFAULT 10,              -- 0-100
  expected_date   DATETIME,
  source          VARCHAR(32),
  owner_user_id   BIGINT,
  products        TEXT,                          -- JSON 关联产品
  notes           TEXT,
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_customer (customer_id),
  INDEX idx_stage (stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商机';

CREATE TABLE IF NOT EXISTS biz_quote (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL DEFAULT 1,
  customer_id     BIGINT NOT NULL,
  opportunity_id  BIGINT,
  code            VARCHAR(32) UNIQUE,
  title           VARCHAR(256),
  total_amount    DECIMAL(15,2) DEFAULT 0,
  discount        DECIMAL(5,2) DEFAULT 0,       -- 折扣率
  final_amount    DECIMAL(15,2) DEFAULT 0,
  currency        VARCHAR(8) DEFAULT 'CNY',
  valid_until     DATETIME,
  status          VARCHAR(16) DEFAULT '草稿',    -- 草稿/审批中/已发送/已接受/已拒绝
  items           TEXT,                          -- JSON 报价明细
  notes           TEXT,
  created_by      BIGINT,
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_customer (customer_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价单';

CREATE TABLE IF NOT EXISTS biz_contract (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL DEFAULT 1,
  customer_id     BIGINT NOT NULL,
  opportunity_id  BIGINT,
  quote_id        BIGINT,
  code            VARCHAR(32) UNIQUE,
  title           VARCHAR(256),
  amount          DECIMAL(15,2) DEFAULT 0,
  currency        VARCHAR(8) DEFAULT 'CNY',
  sign_date       DATETIME,
  start_date      DATETIME,
  end_date        DATETIME,
  status          VARCHAR(16) DEFAULT '执行中',  -- 草签/执行中/已完结/已终止
  payment_terms   TEXT,
  attachments     TEXT,                          -- JSON 附件
  notes           TEXT,
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_customer (customer_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同';

CREATE TABLE IF NOT EXISTS biz_order (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL DEFAULT 1,
  customer_id     BIGINT NOT NULL,
  contract_id     BIGINT,
  code            VARCHAR(32) UNIQUE,
  amount          DECIMAL(15,2) DEFAULT 0,
  paid            DECIMAL(15,2) DEFAULT 0,
  status          VARCHAR(16) DEFAULT '待付款',  -- 待付款/部分付款/已付款/已发货/已完成
  delivery_date   DATETIME,
  notes           TEXT,
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';

CREATE TABLE IF NOT EXISTS biz_payment (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL DEFAULT 1,
  order_id        BIGINT NOT NULL,
  amount          DECIMAL(15,2) NOT NULL,
  method          VARCHAR(32),                  -- 银行转账/支付宝/微信
  status          VARCHAR(16) DEFAULT '已收款',  -- 待收款/已收款/已退款
  paid_at         DATETIME,
  notes           TEXT,
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回款';

CREATE TABLE IF NOT EXISTS biz_product (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL DEFAULT 1,
  code            VARCHAR(32) UNIQUE,
  name            VARCHAR(128) NOT NULL,
  category        VARCHAR(64),
  price           DECIMAL(15,2) DEFAULT 0,
  unit             VARCHAR(16) DEFAULT '元',
  description     TEXT,
  status          INT DEFAULT 1,
  stock           INT DEFAULT 0,
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品/SKU';

CREATE TABLE IF NOT EXISTS biz_service (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL DEFAULT 1,
  code            VARCHAR(32) UNIQUE,
  name            VARCHAR(128) NOT NULL,
  category        VARCHAR(64),
  price           DECIMAL(15,2) DEFAULT 0,
  description     TEXT,
  sla_hours       INT DEFAULT 24,
  status          INT DEFAULT 1,
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务';

CREATE TABLE IF NOT EXISTS biz_expense (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL DEFAULT 1,
  order_id        BIGINT,
  category        VARCHAR(32),                  -- 差旅/招待/物料
  amount          DECIMAL(15,2) NOT NULL,
  happened_at     DATETIME,
  notes           TEXT,
  created_by      BIGINT,
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用';

-- 漏补: 5 张表 (上线前关键)

-- ---------------------------------------------------------------------------
-- 多智能体案例 (Agent 案例库, 首页推荐用)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS agent_multi_agent_case;
CREATE TABLE agent_multi_agent_case (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    case_key        VARCHAR(64)  NOT NULL COMMENT '业务唯一 key, 前端按它索引',
    title           VARCHAR(255) NOT NULL,
    summary         VARCHAR(512),
    description     TEXT,
    domain          VARCHAR(64)  COMMENT '行业 marketing/legal/research/training/...',
    agent_spec      TEXT         COMMENT 'JSON: 智能体节点列表 + 工具 + 流程图',
    flow_spec       TEXT         COMMENT 'JSON: 可执行的 workflow 步骤',
    final_output    TEXT         COMMENT 'JSON: 完整产出 (最终报告/训练数据/部署清单)',
    kpis            TEXT         COMMENT 'JSON: 效果指标 (耗时/准确率/拒答率)',
    featured        INT          DEFAULT 0 COMMENT '是否首页推荐 1=是 0=否',
    create_by       BIGINT,
    update_by       BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_case_key (case_key),
    KEY idx_domain (domain),
    KEY idx_featured (featured)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '多智能体案例';

-- ---------------------------------------------------------------------------
-- 文件元数据 (ai-platform-files 服务, 分片上传/OSS 抽象)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS file_object;
CREATE TABLE file_object (
    file_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键, 自增',
    object_key      VARCHAR(512) NOT NULL COMMENT 'Provider 无关的存储 key (例: kb/2025/12/abc.pdf)',
    original_name   VARCHAR(255) NOT NULL COMMENT '上传时的原始文件名',
    content_type    VARCHAR(128) COMMENT 'MIME (Tika 嗅探)',
    size_bytes      BIGINT       NOT NULL COMMENT '文件大小, 字节',
    bucket          VARCHAR(64)  COMMENT '逻辑桶 (kb/corpus/agent-asset/...)',
    sha256          VARCHAR(64)  COMMENT 'SHA-256 hex, 去重 + 完整性校验',
    uploader        VARCHAR(64)  COMMENT '上传者 username',
    description     VARCHAR(512) COMMENT '上传者描述 / 标签',
    status          INT          DEFAULT 1 COMMENT '1=正常 0=已删除',
    create_by       BIGINT,
    update_by       BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (file_id),
    UNIQUE KEY uk_object_key (object_key),
    KEY idx_bucket (bucket),
    KEY idx_sha256 (sha256)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件元数据 (字节存 OSS/本地)';

-- ---------------------------------------------------------------------------
-- Seata 演示: 智能体调用日志 (ReAct 每跑一次一行, traceId 聚合)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS agent_invoke_log;
CREATE TABLE agent_invoke_log (
    id              BIGINT       NOT NULL,
    trace_id        VARCHAR(64)  NOT NULL COMMENT '链路追踪 ID (ELK / Loki 聚合用)',
    user_id         BIGINT       COMMENT '调用人 userId',
    agent_code      VARCHAR(64)  COMMENT '智能体编码',
    prompt          TEXT         COMMENT '用户 prompt',
    response        TEXT         COMMENT 'AI 回答',
    tokens          BIGINT       COMMENT '消耗 token',
    status          INT          DEFAULT 1 COMMENT '1=成功 0=失败',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trace (trace_id),
    KEY idx_user (user_id),
    KEY idx_agent (agent_code),
    KEY idx_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '智能体调用日志';

-- ---------------------------------------------------------------------------
-- Seata 演示: 使用统计 (每日一行, Dashboard 报表)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS usage_stats;
CREATE TABLE usage_stats (
    id              BIGINT       NOT NULL,
    stat_date       VARCHAR(16)  NOT NULL COMMENT 'yyyy-MM-dd',
    agent_code      VARCHAR(64)  NOT NULL,
    invoke_count    BIGINT       DEFAULT 0,
    token_total     BIGINT       DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_date_agent (stat_date, agent_code),
    KEY idx_date (stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '使用统计 (按日)';

-- ---------------------------------------------------------------------------
-- Seata 演示: 用户积分 (每次 ReAct 结束扣减, 余额不足触发全局回滚)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS user_credits;
CREATE TABLE user_credits (
    id              BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    username        VARCHAR(64)  NOT NULL,
    credits         BIGINT       DEFAULT 0 COMMENT '剩余可用',
    consumed        BIGINT       DEFAULT 0 COMMENT '累计已消耗',
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user (user_id),
    KEY idx_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户 AI 额度';

-- =============================================================================
--  SECTION 2: 种子数据 (admin / 角色 / 业务客户等)
-- =============================================================================

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

-- =============================================================================
--  SECTION 3: 收尾 + 验证
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 1;

-- 验证: 显示全部 32 张表
SHOW TABLES;

-- 种子统计 (跑完应看到非 0)
SELECT 'sys_user' AS tbl, COUNT(*) AS cnt FROM sys_user
UNION ALL SELECT 'sys_role', COUNT(*) FROM sys_role
UNION ALL SELECT 'sys_user_role', COUNT(*) FROM sys_user_role
UNION ALL SELECT 'sys_user_tenant', COUNT(*) FROM sys_user_tenant
UNION ALL SELECT 'biz_customer', COUNT(*) FROM biz_customer
UNION ALL SELECT 'biz_opportunity', COUNT(*) FROM biz_opportunity
UNION ALL SELECT 'biz_order', COUNT(*) FROM biz_order
UNION ALL SELECT 'biz_product', COUNT(*) FROM biz_product
UNION ALL SELECT 'user_credits', COUNT(*) FROM user_credits
UNION ALL SELECT 'agent_multi_agent_case', COUNT(*) FROM agent_multi_agent_case;

-- =============================================================================
--  SECTION 4: 其它库的初始化 (Nacos / Seata, 非本文件范围)
-- =============================================================================
--
--  本项目默认连接以下 3 个 MySQL 库, 本文件只建 ai_platform.
--  其余 2 个库请按下面命令跑 (仅启用时需要):
--
--  1) Nacos 配置库:
--     mysql -uroot -p951127 -e 'CREATE DATABASE IF NOT EXISTS nacos_config DEFAULT CHARSET utf8mb4;'
--     curl -O https://raw.githubusercontent.com/alibaba/nacos/develop/distribution/conf/mysql-schema.sql
--     mysql -uroot -p951127 nacos_config < mysql-schema.sql
--
--  2) Seata 库:
--     mysql -uroot -p951127 -e 'CREATE DATABASE IF NOT EXISTS seata DEFAULT CHARSET utf8mb4;'
--     curl -O https://raw.githubusercontent.com/seata/seata/2.0.0/script/server/db/mysql.sql
--     mysql -uroot -p951127 seata < mysql.sql
--
--  跳过 Nacos/Seata 不影响: 服务本地配置 + 降级到 LOCAL 事务.
-- =============================================================================
