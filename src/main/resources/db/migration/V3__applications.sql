-- V3 applications / daily allocations

-- Technical helper table: business reference numbers are allocated from a
-- locked counter row, never from MAX(id)+1.
CREATE TABLE reference_sequence (
    sequence_year INT    NOT NULL,
    last_issued  BIGINT NOT NULL,
    CONSTRAINT pk_reference_sequence PRIMARY KEY (sequence_year)
) ENGINE = InnoDB;
CREATE TABLE course_application (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    reference_no       VARCHAR(30)    NOT NULL,
    employee_id        BIGINT         NOT NULL,
    approver_id        BIGINT         NOT NULL,
    catalogue_id       BIGINT         NULL,
    category_code      VARCHAR(20)    NOT NULL,
    course_title       VARCHAR(200)   NOT NULL,
    provider_name      VARCHAR(160)   NOT NULL,
    start_date         DATE           NOT NULL,
    end_date           DATE           NOT NULL,
    start_session      VARCHAR(2)     NOT NULL,
    end_session        VARCHAR(2)     NOT NULL,
    course_fee         DECIMAL(12, 2) NOT NULL,
    justification      VARCHAR(2000)  NOT NULL,
    work_dissemination VARCHAR(2000)  NULL,
    status             VARCHAR(20)    NOT NULL,
    submitted_at       DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    reviewed_by        BIGINT         NULL,
    reviewed_at        DATETIME(6)    NULL,
    review_comment     VARCHAR(2000)  NULL,
    completion_comment VARCHAR(2000)  NULL,
    completed_at       DATETIME(6)    NULL,
    cancel_reason      VARCHAR(2000)  NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    client_request_id  VARCHAR(36)    NOT NULL,
    create_request_hash VARCHAR(64)   NOT NULL,
    CONSTRAINT pk_course_application PRIMARY KEY (id),
    CONSTRAINT uk_application_reference UNIQUE (reference_no),
    CONSTRAINT uk_application_request UNIQUE (employee_id, client_request_id),
    CONSTRAINT fk_application_employee FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE RESTRICT,
    CONSTRAINT fk_application_approver FOREIGN KEY (approver_id) REFERENCES employee (id) ON DELETE RESTRICT,
    CONSTRAINT fk_application_reviewer FOREIGN KEY (reviewed_by) REFERENCES employee (id) ON DELETE RESTRICT,
    CONSTRAINT fk_application_catalogue FOREIGN KEY (catalogue_id) REFERENCES course_catalogue (id) ON DELETE RESTRICT,
    CONSTRAINT fk_application_category FOREIGN KEY (category_code) REFERENCES course_category (code) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE INDEX ix_application_employee_status ON course_application (employee_id, status, start_date, end_date);
CREATE INDEX ix_application_approver_status ON course_application (approver_id, status, employee_id);
CREATE INDEX ix_application_status_dates ON course_application (status, start_date, end_date);

CREATE TABLE application_day (
    application_id BIGINT      NOT NULL,
    training_date  DATE        NOT NULL,
    units          INT         NOT NULL,
    session_code   VARCHAR(4)  NOT NULL,
    CONSTRAINT pk_application_day PRIMARY KEY (application_id, training_date),
    CONSTRAINT fk_application_day_application FOREIGN KEY (application_id) REFERENCES course_application (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE INDEX ix_application_day_date ON application_day (training_date, application_id);
