ALTER TABLE interview_session
    ADD COLUMN topics           jsonb         NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN difficulty       text          NOT NULL DEFAULT 'intermediate',
    ADD COLUMN duration_minutes integer       NOT NULL DEFAULT 15,
    ADD COLUMN review_ratio     numeric(3, 2) NOT NULL DEFAULT 0.40;

ALTER TABLE session_turn
    ADD COLUMN common_mistakes_json jsonb   NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN follow_up_depth      integer NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uq_question_bank_question_text ON question_bank (question_text);
