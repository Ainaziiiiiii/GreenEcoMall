-- Columns added to User entity after the initial schema

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS finik_phone          VARCHAR(255),
    ADD COLUMN IF NOT EXISTS registration_plan    VARCHAR(20)  NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS accelerator_assisted BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS acc_assisted_stage2  BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fast_start_number    INT,
    ADD COLUMN IF NOT EXISTS code_word            VARCHAR(255);
