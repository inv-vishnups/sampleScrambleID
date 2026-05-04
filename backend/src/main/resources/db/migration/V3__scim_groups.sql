CREATE TABLE scim_groups (
    id            UUID         NOT NULL PRIMARY KEY,
    display_name  VARCHAR(255) NOT NULL,
    external_id   VARCHAR(255),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_scim_groups_display_name UNIQUE (display_name),
    CONSTRAINT uk_scim_groups_external_id UNIQUE (external_id)
);

CREATE TABLE scim_group_members (
    group_id UUID NOT NULL,
    user_id  UUID NOT NULL,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_sgm_group FOREIGN KEY (group_id) REFERENCES scim_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_sgm_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_sgm_user ON scim_group_members (user_id);
