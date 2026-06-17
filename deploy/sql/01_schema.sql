-- ============================================================================
-- AI Agent Platform - Schema
-- All business tables are multi-tenant (tenant_id) and inherit from BaseEntity
-- (id, tenant_id, create_by, update_by, create_time, update_time, deleted)
-- ============================================================================

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

-- ====================================================
-- 业务表 (洽谈/商机/合同/报价/订单/费用/产品/服务)
-- ====================================================

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
