package io.github.gumwoo.aiinterview.domain.question;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

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

@Entity
@Table(name = "question_bank")
public class QuestionBankItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String topic;

	@Column(name = "concept_key", nullable = false)
	private String conceptKey;

	@Column(nullable = false)
	private String difficulty;

	@Column(name = "question_text", nullable = false)
	private String questionText;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "reference_points_json", nullable = false)
	private ReferencePoints referencePoints;

	@Column(name = "rubric_version", nullable = false)
	private int rubricVersion = 1;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private QuestionOrigin origin;

	@Column(name = "source_ref")
	private String sourceRef;

	@Column(name = "source_commit")
	private String sourceCommit;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected QuestionBankItem() {
	}

	public QuestionBankItem(String topic, String conceptKey, String difficulty, String questionText,
			ReferencePoints referencePoints, QuestionOrigin origin, String sourceRef, String sourceCommit,
			Instant createdAt) {
		this.topic = topic;
		this.conceptKey = conceptKey;
		this.difficulty = difficulty;
		this.questionText = questionText;
		this.referencePoints = referencePoints;
		this.origin = origin;
		this.sourceRef = sourceRef;
		this.sourceCommit = sourceCommit;
		this.createdAt = createdAt;
	}

	/** 시드 파일이 바뀌면 기준 답안과 근거를 맞춘다. 바뀐 것이 있으면 true다. */
	public boolean refresh(ReferencePoints points, String ref, String commit) {
		if (points.equals(referencePoints) && Objects.equals(ref, sourceRef) && Objects.equals(commit, sourceCommit)) {
			return false;
		}
		if (!points.equals(referencePoints)) {
			rubricVersion++;
		}
		referencePoints = points;
		sourceRef = ref;
		sourceCommit = commit;
		return true;
	}

	public Long getId() {
		return id;
	}

	public String getTopic() {
		return topic;
	}

	public String getConceptKey() {
		return conceptKey;
	}

	public String getQuestionText() {
		return questionText;
	}

	public ReferencePoints getReferencePoints() {
		return referencePoints;
	}

	public QuestionOrigin getOrigin() {
		return origin;
	}

	public record ReferencePoints(List<String> expectedPoints, List<String> commonMistakes) {
	}

	public enum QuestionOrigin {

		SEED, GENERATED

	}

}
