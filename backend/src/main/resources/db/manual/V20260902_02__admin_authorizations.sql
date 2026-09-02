CREATE TABLE IF NOT EXISTS admin_authorizations (
    id UUID PRIMARY KEY,
    requester_user_id UUID NOT NULL,
    approver_user_id UUID NOT NULL,
    permission_code VARCHAR(80) NOT NULL,
    operation_key VARCHAR(80) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id UUID,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    consumed_at TIMESTAMPTZ,
    CONSTRAINT fk_admin_authorizations_requester
        FOREIGN KEY (requester_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_admin_authorizations_approver
        FOREIGN KEY (approver_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_admin_authorizations_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_admin_authorizations_status
        CHECK (status IN ('ISSUED', 'RESERVED', 'CONSUMED', 'EXPIRED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_admin_authorizations_requester
    ON admin_authorizations(requester_user_id);

CREATE INDEX IF NOT EXISTS idx_admin_authorizations_expires_at
    ON admin_authorizations(expires_at);
