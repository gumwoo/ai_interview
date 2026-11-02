CREATE TABLE interview_session (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_request_id uuid        NOT NULL UNIQUE,
    scheduled_date    date        NOT NULL,
    source            text        NOT NULL CHECK (source IN ('SCHEDULED', 'MANUAL')),
    status            text        NOT NULL CHECK (status IN ('ASKING', 'LISTENING', 'GRADING', 'EXPLAINING', 'FOLLOW_UP', 'COMPLETED')),
    topic             text,
    started_at        timestamptz NOT NULL DEFAULT now(),
    ended_at          timestamptz
);

CREATE UNIQUE INDEX uq_interview_session_scheduled_date
    ON interview_session (scheduled_date)
    WHERE source = 'SCHEDULED';

CREATE TABLE question_bank (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topic                 text        NOT NULL,
    concept_key           text        NOT NULL,
    difficulty            text        NOT NULL,
    question_text         text        NOT NULL,
    reference_points_json jsonb       NOT NULL,
    rubric_version        integer     NOT NULL DEFAULT 1,
    origin                text        NOT NULL CHECK (origin IN ('SEED', 'GENERATED')),
    source_ref            text,
    source_commit         text,
    created_at            timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_question_bank_topic_difficulty ON question_bank (topic, difficulty);
CREATE INDEX idx_question_bank_concept_key ON question_bank (concept_key);

CREATE TABLE session_turn (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id           bigint      NOT NULL REFERENCES interview_session (id) ON DELETE CASCADE,
    turn_no              integer     NOT NULL,
    question_id          bigint REFERENCES question_bank (id),
    concept_key          text        NOT NULL,
    question_text        text        NOT NULL,
    expected_points_json jsonb       NOT NULL,
    answer_text          text,
    client_request_id    uuid,
    processing_status    text        NOT NULL DEFAULT 'AWAITING_ANSWER'
        CHECK (processing_status IN ('AWAITING_ANSWER', 'GRADING', 'GRADED', 'FAILED')),
    verdict              text CHECK (verdict IN ('CORRECT', 'PARTIAL', 'WRONG', 'UNDETERMINED')),
    feedback_json        jsonb,
    response_ms          integer,
    created_at           timestamptz NOT NULL DEFAULT now(),
    UNIQUE (session_id, turn_no),
    UNIQUE (session_id, client_request_id)
);

CREATE INDEX idx_session_turn_question_id ON session_turn (question_id);

CREATE TABLE review_item (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    concept_key    text        NOT NULL,
    source_turn_id bigint      NOT NULL REFERENCES session_turn (id) ON DELETE CASCADE,
    next_review_at timestamptz NOT NULL,
    streak         integer     NOT NULL DEFAULT 0,
    priority       integer     NOT NULL DEFAULT 0
);

CREATE INDEX idx_review_item_source_turn_id ON review_item (source_turn_id);
CREATE INDEX idx_review_item_next_review_at ON review_item (next_review_at);

CREATE TABLE api_usage_log (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id         bigint REFERENCES interview_session (id) ON DELETE SET NULL,
    model_id           text           NOT NULL,
    input_tokens       integer        NOT NULL,
    output_tokens      integer        NOT NULL,
    estimated_cost_usd numeric(12, 6) NOT NULL,
    created_at         timestamptz    NOT NULL DEFAULT now()
);

CREATE INDEX idx_api_usage_log_session_id ON api_usage_log (session_id);
CREATE INDEX idx_api_usage_log_created_at ON api_usage_log (created_at);
