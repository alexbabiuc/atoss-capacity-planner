-- V1__initial_schema.sql
-- Capacity Planning Tool — initial schema
-- Flyway owns DDL; Hibernate is set to validate-only.

-- ── Supply side ───────────────────────────────────────────────────────────────

CREATE TABLE skills (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name     VARCHAR(120) NOT NULL UNIQUE,
    category VARCHAR(60)
);

CREATE TABLE teams (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(120) NOT NULL UNIQUE,
    overhead_factor DOUBLE PRECISION NOT NULL DEFAULT 0.15,
    support_factor  DOUBLE PRECISION NOT NULL DEFAULT 0.10
);

CREATE TABLE people (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(120) NOT NULL,
    team_id           UUID NOT NULL REFERENCES teams(id) ON DELETE RESTRICT,
    base_availability DOUBLE PRECISION NOT NULL DEFAULT 1.0
);

CREATE TABLE person_skills (
    person_id   UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    skill_id    UUID NOT NULL REFERENCES skills(id)  ON DELETE RESTRICT,
    proficiency VARCHAR(20) NOT NULL CHECK (proficiency IN ('FAMILIAR','PROFICIENT','EXPERT')),
    PRIMARY KEY (person_id, skill_id)
);

CREATE TABLE availability_overrides (
    id              BIGSERIAL PRIMARY KEY,
    person_id       UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    override_start  DATE NOT NULL,
    override_end    DATE,           -- NULL = open-ended (attrition, permanent change)
    override_factor DOUBLE PRECISION NOT NULL CHECK (override_factor BETWEEN 0 AND 1),
    override_reason TEXT NOT NULL
);
CREATE INDEX idx_avail_overrides_person ON availability_overrides(person_id, override_start);

-- ── Demand side ───────────────────────────────────────────────────────────────

CREATE TABLE initiatives (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  VARCHAR(200) NOT NULL,
    description           TEXT,
    top_down_estimate     DOUBLE PRECISION NOT NULL,  -- person-days
    start_date            DATE,
    target_delivery_date  DATE NOT NULL,
    priority              INT NOT NULL DEFAULT 100,
    owner_name            VARCHAR(120),
    status                VARCHAR(20) NOT NULL DEFAULT 'PROPOSED'
                          CHECK (status IN ('PROPOSED','COMMITTED','AT_RISK','DELIVERED','CANCELLED'))
);

CREATE TABLE epics (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    initiative_id UUID REFERENCES initiatives(id) ON DELETE SET NULL,  -- nullable: orphan epics allowed
    team_id       UUID NOT NULL REFERENCES teams(id) ON DELETE RESTRICT,
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    estimate      DOUBLE PRECISION NOT NULL,   -- person-days
    start_date    DATE NOT NULL,
    due_date      DATE NOT NULL,
    priority      VARCHAR(10) NOT NULL DEFAULT 'SHOULD'
                  CHECK (priority IN ('MUST','SHOULD','COULD','WONT')),
    status        VARCHAR(20) NOT NULL DEFAULT 'COMMITTED'
                  CHECK (status IN ('COMMITTED','AT_RISK','DEFERRED','DONE'))
);
CREATE INDEX idx_epics_team      ON epics(team_id);
CREATE INDEX idx_epics_initiative ON epics(initiative_id);

CREATE TABLE epic_required_skills (
    id              BIGSERIAL PRIMARY KEY,
    epic_id         UUID NOT NULL REFERENCES epics(id) ON DELETE CASCADE,
    skill_id        UUID NOT NULL REFERENCES skills(id) ON DELETE RESTRICT,
    min_proficiency VARCHAR(20) NOT NULL CHECK (min_proficiency IN ('FAMILIAR','PROFICIENT','EXPERT')),
    demand_pd       DOUBLE PRECISION NOT NULL
);

-- ── Change log (append-only) ──────────────────────────────────────────────────

CREATE TABLE change_sets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp   TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor       VARCHAR(120) NOT NULL,
    source      VARCHAR(20)  NOT NULL CHECK (source IN ('CHANGE_REQUEST','TEAM_FEEDBACK')),
    reason      TEXT NOT NULL,
    scenario_id UUID         -- optional ref to originating scenario (not FK — scenarios are ephemeral)
);

CREATE TABLE change_set_deltas (
    id             BIGSERIAL PRIMARY KEY,
    change_set_id  UUID NOT NULL REFERENCES change_sets(id) ON DELETE CASCADE,
    entity_type    VARCHAR(50) NOT NULL,
    entity_id      VARCHAR(36) NOT NULL,
    field          VARCHAR(100) NOT NULL,
    old_value      TEXT,
    new_value      TEXT
);
CREATE INDEX idx_deltas_entity ON change_set_deltas(entity_type, entity_id);
