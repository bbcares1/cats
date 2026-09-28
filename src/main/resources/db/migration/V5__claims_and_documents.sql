-- V5 claims / documents
CREATE TABLE course_claim (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    application_id         BIGINT         NOT NULL,
    revision               INT            NOT NULL DEFAULT 1,
    amount                 DECIMAL(12, 2) NOT NULL,
    paid_by_employee       BOOLEAN        NOT NULL,
    approver_id            BIGINT         NOT NULL,
    status                 VARCHAR(20)    NOT NULL,
    submitted_at           DATETIME(6)    NOT NULL,
    reviewed_by            BIGINT         NULL,
    review_comment         VARCHAR(2000)  NULL,
    reviewed_at            DATETIME(6)    NULL,
    reimbursed_by          BIGINT         NULL,
    reimbursed_at          DATETIME(6)    NULL,
    reimbursement_reference VARCHAR(80)   NULL,
    version                BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_course_claim PRIMARY KEY (id),
    CONSTRAINT uk_claim_application UNIQUE (application_id),
    CONSTRAINT fk_claim_application FOREIGN KEY (application_id) REFERENCES course_application (id) ON DELETE RESTRICT,
    CONSTRAINT fk_claim_approver FOREIGN KEY (approver_id) REFERENCES employee (id) ON DELETE RESTRICT,
    CONSTRAINT fk_claim_reviewer FOREIGN KEY (reviewed_by) REFERENCES employee (id) ON DELETE RESTRICT,
    CONSTRAINT fk_claim_reimburser FOREIGN KEY (reimbursed_by) REFERENCES employee (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE INDEX ix_claim_approver_status ON course_claim (approver_id, status, submitted_at);

CREATE TABLE claim_document (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    claim_id              BIGINT       NOT NULL,
    claim_revision        INT          NOT NULL,
    document_type         VARCHAR(24)  NOT NULL,
    storage_key           VARCHAR(200) NOT NULL,
    original_name         VARCHAR(200) NOT NULL,
    detected_content_type VARCHAR(100) NOT NULL,
    size_bytes            BIGINT       NOT NULL,
    sha256                VARCHAR(64)  NOT NULL,
    uploaded_by           BIGINT       NOT NULL,
    uploaded_at           DATETIME(6)  NOT NULL,
    CONSTRAINT pk_claim_document PRIMARY KEY (id),
    CONSTRAINT uk_claim_document_revision UNIQUE (claim_id, claim_revision, document_type),
    CONSTRAINT uk_claim_document_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_document_claim FOREIGN KEY (claim_id) REFERENCES course_claim (id) ON DELETE RESTRICT,
    CONSTRAINT fk_document_uploader FOREIGN KEY (uploaded_by) REFERENCES employee (id) ON DELETE RESTRICT
) ENGINE = InnoDB;
