package io.github.gumwoo.aiinterview.application.interview;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.GradingResult;
import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.NextAction;
import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.Verdict;
import io.github.gumwoo.aiinterview.domain.session.InterviewSession;
import io.github.gumwoo.aiinterview.domain.session.InterviewSession.SessionSource;
import io.github.gumwoo.aiinterview.domain.session.InterviewSession.SessionStatus;
import io.github.gumwoo.aiinterview.domain.session.SessionTurn;
import io.github.gumwoo.aiinterview.domain.session.SessionTurn.ProcessingStatus;

/**
 * 면접 서비스의 입력과 출력.
 */
public final class InterviewModels {

	private InterviewModels() {
	}

	public record StartSession(SessionSource source, LocalDate scheduledDate, List<String> topics, String difficulty,
			int durationMinutes, BigDecimal reviewRatio, UUID clientRequestId) {
	}

	public record SubmitAnswer(Long turnId, String recognizedText, UUID clientRequestId) {
	}

	public record SessionView(Long sessionId, SessionStatus status, Long turnId, String questionText,
			long durationLimitSeconds, Instant startedAt, Instant endedAt, List<TurnView> turns) {

		static SessionView of(InterviewSession session, List<SessionTurn> turns) {
			SessionTurn current = session.isCompleted() || turns.isEmpty() ? null : turns.get(turns.size() - 1);
			return new SessionView(session.getId(), session.getStatus(), current != null ? current.getId() : null,
					current != null ? current.getQuestionText() : null, session.getDurationMinutes() * 60L,
					session.getStartedAt(), session.getEndedAt(), turns.stream().map(TurnView::of).toList());
		}

	}

	public record TurnView(Long turnId, int turnNo, String questionText, String answerText,
			ProcessingStatus processingStatus, Verdict verdict, String feedbackToSpeak) {

		static TurnView of(SessionTurn turn) {
			return new TurnView(turn.getId(), turn.getTurnNo(), turn.getQuestionText(), turn.getAnswerText(),
					turn.getProcessingStatus(), turn.getVerdict(),
					turn.getFeedback() != null ? turn.getFeedback().feedbackToSpeak() : null);
		}

	}

	public record AnswerView(Long sessionId, SessionStatus sessionStatus, Long turnId, Verdict verdict,
			String feedbackToSpeak, NextAction nextAction, List<String> correctPoints, List<String> missingPoints,
			List<String> incorrectPoints, Long nextTurnId, String nextQuestionText) {

		static AnswerView of(InterviewSession session, SessionTurn answered, SessionTurn next) {
			GradingResult result = answered.getFeedback();
			return new AnswerView(session.getId(), session.getStatus(), answered.getId(), result.verdict(),
					result.feedbackToSpeak(), result.nextAction(), result.correctPoints(), result.missingPoints(),
					result.incorrectPoints(), next != null ? next.getId() : null,
					next != null ? next.getQuestionText() : null);
		}

	}

	public record HintView(Long turnId, String hintText) {
	}

	public record SummaryView(Long sessionId, SessionStatus status, int answeredCount, Map<Verdict, Long> verdictCounts,
			List<String> missingPoints, List<String> incorrectPoints) {
	}

}
