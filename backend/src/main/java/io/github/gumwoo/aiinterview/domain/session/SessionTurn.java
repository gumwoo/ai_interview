package io.github.gumwoo.aiinterview.domain.session;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.GradingResult;
import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.Verdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 질문 하나와 그 답변, 판정을 기록한다. 질문과 채점 기준은 사후 재현을 위해 스냅샷으로 남긴다.
 */
@Entity
@Table(name = "session_turn")
public class SessionTurn {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "session_id", nullable = false)
	private Long sessionId;

	@Column(name = "turn_no", nullable = false)
	private int turnNo;

	/** 꼬리질문은 질문 은행에 없으므로 null이다. */
	@Column(name = "question_id")
	private Long questionId;

	@Column(name = "concept_key", nullable = false)
	private String conceptKey;

	@Column(name = "question_text", nullable = false)
	private String questionText;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "expected_points_json", nullable = false)
	private List<String> expectedPoints;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "common_mistakes_json", nullable = false)
	private List<String> commonMistakes;

	@Column(name = "follow_up_depth", nullable = false)
	private int followUpDepth;

	@Column(name = "answer_text")
	private String answerText;

	@Column(name = "client_request_id")
	private UUID clientRequestId;

	@Enumerated(EnumType.STRING)
	@Column(name = "processing_status", nullable = false)
	private ProcessingStatus processingStatus;

	@Enumerated(EnumType.STRING)
	private Verdict verdict;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "feedback_json")
	private GradingResult feedback;

	@Column(name = "response_ms")
	private Integer responseMs;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected SessionTurn() {
	}

	public SessionTurn(Long sessionId, int turnNo, Long questionId, String conceptKey, String questionText,
			List<String> expectedPoints, List<String> commonMistakes, int followUpDepth, Instant createdAt) {
		this.sessionId = sessionId;
		this.turnNo = turnNo;
		this.questionId = questionId;
		this.conceptKey = conceptKey;
		this.questionText = questionText;
		this.expectedPoints = expectedPoints;
		this.commonMistakes = commonMistakes;
		this.followUpDepth = followUpDepth;
		this.processingStatus = ProcessingStatus.AWAITING_ANSWER;
		this.createdAt = createdAt;
	}

	public void recordGrading(GradingResult result, int responseMs) {
		this.processingStatus = ProcessingStatus.GRADED;
		this.verdict = result.verdict();
		this.feedback = result;
		this.responseMs = responseMs;
	}

	public void markFailed() {
		this.processingStatus = ProcessingStatus.FAILED;
	}

	public Long getId() {
		return id;
	}

	public Long getSessionId() {
		return sessionId;
	}

	public int getTurnNo() {
		return turnNo;
	}

	public Long getQuestionId() {
		return questionId;
	}

	public String getConceptKey() {
		return conceptKey;
	}

	public String getQuestionText() {
		return questionText;
	}

	public List<String> getExpectedPoints() {
		return expectedPoints;
	}

	public List<String> getCommonMistakes() {
		return commonMistakes;
	}

	public int getFollowUpDepth() {
		return followUpDepth;
	}

	public String getAnswerText() {
		return answerText;
	}

	public UUID getClientRequestId() {
		return clientRequestId;
	}

	public ProcessingStatus getProcessingStatus() {
		return processingStatus;
	}

	public Verdict getVerdict() {
		return verdict;
	}

	public GradingResult getFeedback() {
		return feedback;
	}

	public enum ProcessingStatus {

		AWAITING_ANSWER, GRADING, GRADED, FAILED

	}

}
