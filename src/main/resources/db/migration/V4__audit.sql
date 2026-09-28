-- V4 audit timeline
CREATE TABLE audit_event (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    aggregate_type    VARCHAR(32)  NOT NULL,
    aggregate_key     VARCHAR(64)  NOT NULL,
    event_type        VARCHAR(50)  NOT NULL,
    actor_employee_id BIGINT       NULL,
    from_state        VARCHAR(24)  NULL,
    to_state          VARCHAR(24)  NULL,
    reason            VARCHAR(2000) NULL,
    snapshot_json     JSON         NULL,
    correlation_id    VARCHAR(36)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_employee_id) REFERENCES employee (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE INDEX ix_audit_aggregate ON audit_event (aggregate_type, aggregate_key, created_at, id);
CREATE INDEX ix_audit_correlation ON audit_event (correlation_id);
