CREATE TABLE IF NOT EXISTS user_credits (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(64),
    credits BIGINT NOT NULL,
    consumed BIGINT DEFAULT 0,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO user_credits (id, user_id, username, credits, consumed) VALUES (1, 1, 'admin', 10000, 0);
INSERT INTO user_credits (id, user_id, username, credits, consumed) VALUES (2, 2, 'demo',  100,   0);
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
