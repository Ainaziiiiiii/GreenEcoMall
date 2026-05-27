CREATE TABLE login_history (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    phone      VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_login_history_user_id ON login_history(user_id);
