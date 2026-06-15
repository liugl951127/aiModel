CREATE TABLE IF NOT EXISTS usage_stats (
    id BIGINT PRIMARY KEY,
    stat_date VARCHAR(16),
    agent_code VARCHAR(64),
    invoke_count BIGINT,
    token_total BIGINT
);
CREATE TABLE IF NOT EXISTS undo_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    xid VARCHAR(100) NOT NULL,
    context VARCHAR(128) NOT NULL,
    rollback_info BLOB NOT NULL,
    log_status INT NOT NULL,
    log_created TIMESTAMP NOT NULL,
    log_modified TIMESTAMP NOT NULL,
    ext VARCHAR(100) DEFAULT NULL
);
