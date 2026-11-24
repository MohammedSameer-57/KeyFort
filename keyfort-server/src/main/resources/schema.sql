-- KeyFort Production PostgreSQL Schema
-- Validated against JPA entities with spring.jpa.hibernate.ddl-auto=validate

CREATE TABLE IF NOT EXISTS access_events (
    id BIGSERIAL PRIMARY KEY,
    at TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    ip VARCHAR(64) NOT NULL,
    geo VARCHAR(64) NOT NULL,
    success BOOLEAN NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_events_user_at ON access_events (user_id, at);

CREATE TABLE IF NOT EXISTS devices (
    id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    device_name VARCHAR(128) NOT NULL,
    state VARCHAR(32) NOT NULL,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_devices_user_id ON devices (user_id);

CREATE TABLE IF NOT EXISTS entries (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    site VARCHAR(256) NOT NULL,
    username VARCHAR(256) NOT NULL,
    sealed_password BYTEA NOT NULL,
    key_version INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_entries_user_id ON entries (user_id);

CREATE TABLE IF NOT EXISTS ledger_blocks (
    seq BIGINT PRIMARY KEY,
    at TIMESTAMP WITH TIME ZONE NOT NULL,
    type VARCHAR(64) NOT NULL,
    payload VARCHAR(1024) NOT NULL,
    prev_hash VARCHAR(64) NOT NULL,
    hash VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS vaults (
    user_id VARCHAR(128) PRIMARY KEY,
    iterations INTEGER NOT NULL,
    recovery_pub BYTEA NOT NULL,
    kdf_salt BYTEA NOT NULL,
    wrapped_dek BYTEA NOT NULL,
    recovery_wrapped_dek BYTEA NOT NULL,
    key_version INTEGER NOT NULL DEFAULT 1,
    rotation_pending BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS guest_passes (
    id VARCHAR(64) PRIMARY KEY,
    entry_id VARCHAR(64) NOT NULL,
    scope VARCHAR(64) NOT NULL,
    secret_hash BYTEA NOT NULL,
    state VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    credential_ref VARCHAR(128),
    owner_user_id VARCHAR(64),
    session_id VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS processed_connector_events (
    event_id VARCHAR(64) PRIMARY KEY,
    ticket_id VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
