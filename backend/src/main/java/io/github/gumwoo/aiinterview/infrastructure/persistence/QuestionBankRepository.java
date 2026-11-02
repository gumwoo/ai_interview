package io.github.gumwoo.aiinterview.infrastructure.persistence;

import java.util.Collection;
import java.util.Optional;

import io.github.gumwoo.aiinterview.domain.question.QuestionBankItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionBankRepository extends JpaRepository<QuestionBankItem, Long> {

	boolean existsByQuestionText(String questionText);

	Optional<QuestionBankItem> findByQuestionText(String questionText);

	/**
	 * 이 세션에서 아직 다루지 않은 개념 중, 가장 오래전에 낸 질문을 고른다. 낸 적 없는 질문이 먼저다.
	 */
	@Query(value = """
			SELECT q.*
			FROM question_bank q
			WHERE q.topic IN (:topics)
			  AND q.difficulty = :difficulty
			  AND NOT EXISTS (
			    SELECT 1 FROM session_turn t
			    WHERE t.session_id = :sessionId AND t.concept_key = q.concept_key
			  )
			ORDER BY (SELECT max(t.created_at) FROM session_turn t WHERE t.question_id = q.id) ASC NULLS FIRST, random()
			LIMIT 1
			""", nativeQuery = true)
	Optional<QuestionBankItem> pickNext(@Param("topics") Collection<String> topics,
			@Param("difficulty") String difficulty, @Param("sessionId") long sessionId);

}
