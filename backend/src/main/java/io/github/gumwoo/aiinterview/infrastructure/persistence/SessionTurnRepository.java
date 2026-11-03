package io.github.gumwoo.aiinterview.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.Verdict;
import io.github.gumwoo.aiinterview.domain.session.SessionTurn;
import io.github.gumwoo.aiinterview.domain.session.SessionTurn.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionTurnRepository extends JpaRepository<SessionTurn, Long> {

	List<SessionTurn> findBySessionIdOrderByTurnNo(Long sessionId);

	Optional<SessionTurn> findByIdAndSessionId(Long id, Long sessionId);

	Optional<SessionTurn> findBySessionIdAndTurnNo(Long sessionId, int turnNo);

	Optional<SessionTurn> findFirstBySessionIdOrderByTurnNoDesc(Long sessionId);

	boolean existsBySessionIdAndClientRequestId(Long sessionId, UUID clientRequestId);

	@Query("""
			SELECT t.sessionId AS sessionId, t.verdict AS verdict, count(t) AS total
			FROM SessionTurn t
			WHERE t.sessionId IN :sessionIds AND t.processingStatus = :status
			GROUP BY t.sessionId, t.verdict
			""")
	List<VerdictCount> countVerdicts(@Param("sessionIds") Collection<Long> sessionIds,
			@Param("status") ProcessingStatus status);

	/**
	 * 답변을 받을 수 있는 턴만 채점 중으로 바꾼다. 동시에 들어온 요청 중 하나만 1행을 갱신한다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			UPDATE session_turn
			SET processing_status = 'GRADING', answer_text = :answer, client_request_id = :requestId
			WHERE id = :turnId
			  AND (processing_status = 'AWAITING_ANSWER'
			       OR (processing_status = 'FAILED' AND client_request_id = :requestId))
			""", nativeQuery = true)
	int claimForGrading(@Param("turnId") Long turnId, @Param("answer") String answer,
			@Param("requestId") UUID requestId);

	/** 프로세스가 채점 도중 죽으면 남는 턴을 재시도할 수 있게 되돌린다. */
	@Modifying
	@Query(value = "UPDATE session_turn SET processing_status = 'FAILED' WHERE processing_status = 'GRADING'",
			nativeQuery = true)
	int failInterruptedGradings();

	interface VerdictCount {

		Long getSessionId();

		Verdict getVerdict();

		long getTotal();

	}

}
