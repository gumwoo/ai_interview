package io.github.gumwoo.aiinterview.domain.session;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
@Table(name = "interview_session")
public class InterviewSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "client_request_id", nullable = false, unique = true)
	private UUID clientRequestId;

	@Column(name = "scheduled_date", nullable = false)
	private LocalDate scheduledDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SessionSource source;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SessionStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private List<String> topics;

	@Column(nullable = false)
	private String difficulty;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Column(name = "review_ratio", nullable = false, precision = 3, scale = 2)
	private BigDecimal reviewRatio;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	protected InterviewSession() {
	}

	public InterviewSession(UUID clientRequestId, LocalDate scheduledDate, SessionSource source, List<String> topics,
			String difficulty, int durationMinutes, BigDecimal reviewRatio, Instant startedAt) {
		this.clientRequestId = clientRequestId;
		this.scheduledDate = scheduledDate;
		this.source = source;
		this.status = SessionStatus.ASKING;
		this.topics = topics;
		this.difficulty = difficulty;
		this.durationMinutes = durationMinutes;
		this.reviewRatio = reviewRatio;
		this.startedAt = startedAt;
	}

	public boolean isCompleted() {
		return status == SessionStatus.COMPLETED;
	}

	public boolean isTimeOver(Instant now) {
		return !now.isBefore(startedAt.plus(Duration.ofMinutes(durationMinutes)));
	}

	public void markGrading() {
		this.status = SessionStatus.GRADING;
	}

	public void markAsking() {
		this.status = SessionStatus.ASKING;
	}

	public void complete(Instant now) {
		if (!isCompleted()) {
			this.status = SessionStatus.COMPLETED;
			this.endedAt = now;
		}
	}

	public Long getId() {
		return id;
	}

	public SessionStatus getStatus() {
		return status;
	}

	public List<String> getTopics() {
		return topics;
	}

	public String getDifficulty() {
		return difficulty;
	}

	public BigDecimal getReviewRatio() {
		return reviewRatio;
	}

	public int getDurationMinutes() {
		return durationMinutes;
	}

	public LocalDate getScheduledDate() {
		return scheduledDate;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public enum SessionSource {

		SCHEDULED, MANUAL

	}

	public enum SessionStatus {

		ASKING, LISTENING, GRADING, EXPLAINING, FOLLOW_UP, COMPLETED

	}

}
