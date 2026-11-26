-- 질문 출처를 FlowTicket 문서에서 클라우드 면접 대비 1~7강으로 바꾼다.
-- 강의 주제가 아닌 질문(FlowTicket 시드와 그 주제로 생성한 질문)은 질문 은행에서 지운다.
-- 지난 면접은 턴에 남긴 질문·기준 답안 스냅샷으로 재현되므로 턴의 질문 참조만 끊는다.
UPDATE session_turn
SET question_id = NULL
WHERE question_id IN (
    SELECT id FROM question_bank
    WHERE topic NOT IN ('Docker_Container', 'Kubernetes_EKS', 'Resource_Autoscaling', 'AWS_Network',
                        'Terraform_IaC', 'CICD_GitOps', 'Cloud_Security')
);

DELETE FROM question_bank
WHERE topic NOT IN ('Docker_Container', 'Kubernetes_EKS', 'Resource_Autoscaling', 'AWS_Network',
                    'Terraform_IaC', 'CICD_GitOps', 'Cloud_Security');

-- 더 이상 낼 질문이 없는 개념은 복습 목록에서도 뺀다.
DELETE FROM review_item r
WHERE NOT EXISTS (SELECT 1 FROM question_bank q WHERE q.concept_key = r.concept_key);
