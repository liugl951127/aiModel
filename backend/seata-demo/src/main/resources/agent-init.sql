CREATE TABLE IF NOT EXISTS agent_invoke_log (
    id BIGINT PRIMARY KEY,
    trace_id VARCHAR(64),
    user_id BIGINT,
    agent_code VARCHAR(64),
    prompt VARCHAR(1024),
    response VARCHAR(1024),
    tokens BIGINT,
    status INT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
