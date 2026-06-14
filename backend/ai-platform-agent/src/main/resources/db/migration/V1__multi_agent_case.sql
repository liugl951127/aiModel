-- ============================================================================
-- AI Agent Platform — agent multi-agent case demo data
-- ============================================================================
-- This file is optional: the MultiAgentCaseSeeder ApplicationRunner will
-- upsert demo rows on startup, so MySQL only needs the table to exist.

CREATE DATABASE IF NOT EXISTS aiplatform_agent
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE aiplatform_agent;

CREATE TABLE IF NOT EXISTS agent_multi_agent_case (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    case_key      VARCHAR(128)    NOT NULL,
    title         VARCHAR(255)    NOT NULL,
    summary       VARCHAR(1024)   NOT NULL DEFAULT '',
    description   TEXT,
    domain        VARCHAR(64)     NOT NULL DEFAULT 'general',
    agent_spec    LONGTEXT        NOT NULL,
    flow_spec     LONGTEXT        NOT NULL,
    final_output  LONGTEXT,
    kpis          LONGTEXT,
    featured      INT             NOT NULL DEFAULT 0,
    create_time   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_case_key (case_key),
    KEY idx_domain_featured (domain, featured)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
