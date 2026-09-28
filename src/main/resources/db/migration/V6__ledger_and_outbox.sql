-- V6 ledger / transactional outbox
CREATE TABLE training_ledger (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    account_id             BIGINT         NOT NULL,
    event_id               BIGINT         NOT NULL,
    application_id         BIGINT         NULL,
    claim_id               BIGINT         NULL,
    entry_type             VARCHAR(40)    NOT NULL,
    reserved_units_delta   INT            NOT NULL DEFAULT 0,
    committed_units_delta  INT            NOT NULL DEFAULT 0,
    reserved_amount_delta  DECIMAL(12, 2) NOT NULL DEFAULT 0,
    committed_amount_delta DECIMAL(12, 2) NOT NULL DEFAULT 0,
    reimbursed_amount_delta DECIMAL(12, 2) NOT NULL DEFAULT 0,
    created_at             DATETIME(6)    NOT NULL,
    CONSTRAINT pk_training_ledger PRIMARY KEY (id),
    CONSTRAINT uk_ledger_event_account UNIQUE (event_id, account_id),
    CONSTRAINT fk_ledger_account FOREIGN KEY (account_id) REFERENCES training_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_event FOREIGN KEY (event_id) REFERENCES audit_event (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_application FOREIGN KEY (application_id) REFERENCES course_application (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_claim FOREIGN KEY (claim_id) REFERENCES course_claim (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE INDEX ix_ledger_account ON training_ledger (account_id, created_at, id);
CREATE INDEX ix_ledger_application ON training_ledger (application_id);
CREATE INDEX ix_ledger_claim ON training_ledger (claim_id);

CREATE TABLE email_outbox (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    event_id              BIGINT       NOT NULL,
    recipient_employee_id BIGINT       NOT NULL,
    recipient_email       VARCHAR(254) NOT NULL,
    template_code         VARCHAR(40)  NOT NULL,
    payload_json          JSON         NOT NULL,
    status                VARCHAR(16)  NOT NULL,
    attempts              INT          NOT NULL DEFAULT 0,
    next_attempt_at       DATETIME(6)  NOT NULL,
    lease_until           DATETIME(6)  NULL,
    sent_at               DATETIME(6)  NULL,
    last_error            VARCHAR(1000) NULL,
    created_at            DATETIME(6)  NOT NULL,
    CONSTRAINT pk_email_outbox PRIMARY KEY (id),
    CONSTRAINT uk_outbox_event_recipient_template UNIQUE (event_id, recipient_employee_id, template_code),
    CONSTRAINT fk_outbox_event FOREIGN KEY (event_id) REFERENCES audit_event (id) ON DELETE RESTRICT,
    CONSTRAINT fk_outbox_recipient FOREIGN KEY (recipient_employee_id) REFERENCES employee (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE INDEX ix_outbox_status_next ON email_outbox (status, next_attempt_at, id);
