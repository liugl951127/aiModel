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
    status          INT          DEFAULT 1,
    last_login_ip   VARCHAR(64),
    last_login_time DATETIME,
    create_by       BIGINT,
    update_by       BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username, tenant_id)
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
