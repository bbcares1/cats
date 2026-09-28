-- V2 catalogue / holidays / annual accounts
CREATE TABLE course_category (
    code         VARCHAR(20)  NOT NULL,
    display_name VARCHAR(80)  NOT NULL,
    description  VARCHAR(400) NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_course_category PRIMARY KEY (code)
) ENGINE = InnoDB;

CREATE TABLE training_provider (
    id     BIGINT       NOT NULL AUTO_INCREMENT,
    name   VARCHAR(160) NOT NULL,
    active BOOLEAN      NOT NULL DEFAULT TRUE,
    version BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_training_provider PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE INDEX ix_provider_name ON training_provider (name, id);

CREATE TABLE course_catalogue (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    category_code VARCHAR(20)  NOT NULL,
    provider_id  BIGINT        NULL,
    title        VARCHAR(200)  NOT NULL,
    default_fee  DECIMAL(12, 2) NOT NULL DEFAULT 0,
    description  VARCHAR(1000) NULL,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    version      BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_course_catalogue PRIMARY KEY (id),
    CONSTRAINT fk_catalogue_category FOREIGN KEY (category_code) REFERENCES course_category (code) ON DELETE RESTRICT,
    CONSTRAINT fk_catalogue_provider FOREIGN KEY (provider_id) REFERENCES training_provider (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE INDEX ix_catalogue_category_active_title ON course_catalogue (category_code, active, title);

CREATE TABLE public_holiday (
    holiday_date DATE         NOT NULL,
    name         VARCHAR(120) NOT NULL,
    source_note  VARCHAR(400) NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_public_holiday PRIMARY KEY (holiday_date)
) ENGINE = InnoDB;

CREATE TABLE training_account (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    employee_id   BIGINT         NOT NULL,
    calendar_year INT            NOT NULL,
    entitled_units INT           NOT NULL,
    budget_amount DECIMAL(12, 2) NOT NULL,
    version       BIGINT         NOT NULL DEFAULT 0,
    updated_at    DATETIME(6)    NOT NULL,
    CONSTRAINT pk_training_account PRIMARY KEY (id),
    CONSTRAINT uk_account_employee_year UNIQUE (employee_id, calendar_year),
    CONSTRAINT fk_account_employee FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE INDEX ix_account_year ON training_account (calendar_year);
