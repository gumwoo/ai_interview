-- 15강(지원동기·협업) 노션 답변이 바뀌어 질문 문장도 일부 바뀌었다.
-- 시드는 질문 문장으로 같은 질문을 찾으므로, 예전 문장의 질문이 남지 않게 15강 시드를 지우고
-- 서버 시작 시 시드 파일에서 다시 넣는다. 지난 면접은 턴의 스냅샷으로 남기고 질문 참조만 끊는다.
UPDATE session_turn
SET question_id = NULL
WHERE question_id IN (
    SELECT id FROM question_bank
    WHERE topic = 'Motivation_Collaboration' AND origin = 'SEED'
);

DELETE FROM question_bank
WHERE topic = 'Motivation_Collaboration' AND origin = 'SEED';

