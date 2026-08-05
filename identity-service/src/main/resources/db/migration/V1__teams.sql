-- Teams and related tables
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    image_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE teams (
    id VARCHAR(64) PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id),
    name VARCHAR(255) NOT NULL,
    image_url TEXT,
    description TEXT,
    is_public BOOLEAN NOT NULL DEFAULT true,
    invite_code VARCHAR(64) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE team_memberships (
    team_id VARCHAR(64) REFERENCES teams(id) ON DELETE CASCADE,
    user_id VARCHAR(128) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'member',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (team_id, user_id)
);

CREATE INDEX idx_team_memberships_user_id ON team_memberships(user_id);

CREATE TABLE join_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id VARCHAR(64) REFERENCES teams(id) ON DELETE CASCADE,
    user_id VARCHAR(128) NOT NULL,
    invite_code VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by VARCHAR(128)
);

CREATE INDEX idx_join_requests_user_id ON join_requests(user_id);
CREATE INDEX idx_join_requests_team_id_status ON join_requests(team_id, status);
CREATE INDEX idx_join_requests_invite_code ON join_requests(invite_code);