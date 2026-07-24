CREATE TABLE cast_grants (
    token UUID PRIMARY KEY,
    file_id BIGINT NOT NULL,
    created_by VARCHAR(40) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX cast_grants_expires_at_idx ON cast_grants(expires_at);
