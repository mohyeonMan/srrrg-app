CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oauth_accounts (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (provider, provider_user_id),
    CONSTRAINT ck_oauth_provider CHECK (provider IN ('google', 'kakao', 'github'))
);

CREATE TABLE oauth_authorization_requests (
    token_hash VARCHAR(64) PRIMARY KEY,
    state VARCHAR(255) NOT NULL UNIQUE,
    registration_id VARCHAR(20) NOT NULL,
    authorization_uri VARCHAR(1000) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    redirect_uri VARCHAR(1000) NOT NULL,
    scopes VARCHAR(1000) NOT NULL,
    code_verifier VARCHAR(255) NOT NULL,
    code_challenge VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_oauth_authorization_requests_expires_at
    ON oauth_authorization_requests (expires_at);

CREATE TABLE refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family_id ON refresh_tokens (family_id);
