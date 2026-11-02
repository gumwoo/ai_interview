package io.github.gumwoo.aiinterview.application.interview;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.GradingResult;
import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.NextAction;
import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.Verdict;
import io.github.gumwoo.aiinterview.domain.session.SessionTurn;
import org.junit.jupiter.api.Test;

class FollowUpGeneratorTest {

	private final FollowUpGenerator generator = new FollowUpGenerator(new InterviewProperties(30, 2));

	@Test
	void should_use_follow_up_question_from_grading() {
		FollowUpGenerator.FollowUp followUp = generator.decide(turn(0), result(NextAction.FOLLOW_UP, "꼬리", List.of("기준")));

		assertThat(followUp.questionText()).isEqualTo("꼬리");
		assertThat(followUp.expectedPoints()).containsExactly("기준");
	}

	@Test
	void should_recheck_after_explanation() {
		assertThat(generator.decide(turn(0), result(NextAction.EXPLAIN_AND_RECHECK, "확인", List.of("기준")))
			.questionText()).isEqualTo("확인");
	}

	@Test
	void should_repeat_original_question_when_clarifying() {
		FollowUpGenerator.FollowUp followUp = generator.decide(turn(0), result(NextAction.CLARIFY, "", List.of()));

		assertThat(followUp.questionText()).isEqualTo("원래 질문");
		assertThat(followUp.expectedPoints()).containsExactly("원래 기준");
	}

	@Test
	void should_move_on_when_depth_limit_reached() {
		assertThat(generator.decide(turn(2), result(NextAction.FOLLOW_UP, "꼬리", List.of("기준")))).isNull();
		assertThat(generator.decide(turn(2), result(NextAction.CLARIFY, "", List.of()))).isNull();
	}

	@Test
	void should_move_on_when_next_question_requested() {
		assertThat(generator.decide(turn(0), result(NextAction.NEXT_QUESTION, "", List.of()))).isNull();
	}

	@Test
	void should_move_on_when_follow_up_has_no_criteria() {
		assertThat(generator.decide(turn(0), result(NextAction.EXPLAIN_AND_RECHECK, "확인", List.of()))).isNull();
		assertThat(generator.decide(turn(0), result(NextAction.FOLLOW_UP, " ", List.of("기준")))).isNull();
	}

	private static SessionTurn turn(int depth) {
		return new SessionTurn(1L, 1, 1L, "c", "원래 질문", List.of("원래 기준"), List.of(), depth, Instant.EPOCH);
	}

	private static GradingResult result(NextAction action, String question, List<String> points) {
		return new GradingResult(Verdict.PARTIAL, 0.5, List.of(), List.of(), List.of(), "피드백", action, question, points);
	}

}
