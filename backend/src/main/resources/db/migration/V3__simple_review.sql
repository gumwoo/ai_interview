-- 복습은 "틀린 개념을 다음 면접에 다시 묻고, 맞히면 뺀다"로 단순화한다. 복습일과 우선순위는 쓰지 않는다.
ALTER TABLE review_item
    DROP COLUMN next_review_at,
    DROP COLUMN streak,
    DROP COLUMN priority,
    ADD CONSTRAINT uq_review_item_concept_key UNIQUE (concept_key);

ALTER TABLE session_turn
    ADD COLUMN review boolean NOT NULL DEFAULT false;

-- 이미 진행한 면접의 오답도 복습 대상으로 옮긴다. 그 뒤에 새로 물어 맞힌 개념은 뺀다.
INSERT INTO review_item (concept_key, source_turn_id)
SELECT DISTINCT ON (t.concept_key) t.concept_key, t.id
FROM session_turn t
WHERE t.verdict IN ('WRONG', 'PARTIAL')
  AND NOT EXISTS (
    SELECT 1 FROM session_turn later
    WHERE later.concept_key = t.concept_key
      AND later.id > t.id
      AND later.verdict = 'CORRECT'
      AND later.follow_up_depth = 0
  )
ORDER BY t.concept_key, t.id DESC;
