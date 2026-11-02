package io.github.gumwoo.aiinterview.application.interview;

import java.util.List;

import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.GradingResult;
import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.NextAction;
import io.github.gumwoo.aiinterview.domain.session.SessionTurn;
import org.springframework.stereotype.Component;

/**
 * 채점 결과를 해석해 같은 개념을 계속 물을지 정한다. 꼬리질문은 채점 호출이 함께 만들어 주므로 Claude를 다시 부르지 않는다.
 */
@Component
public class FollowUpGenerator {

	private final InterviewProperties properties;

	public FollowUpGenerator(InterviewProperties properties) {
		this.properties = properties;
	}

	/** 이 턴의 답변 뒤에 같은 개념을 더 물어도 되는지 알려 준다. 채점 프롬프트에 함께 넘긴다. */
	public boolean canFollowUp(SessionTurn turn) {
		return turn.getFollowUpDepth() < properties.maxFollowUpDepth();
	}

	/** 같은 개념을 이어 물을 내용. 다음 질문으로 넘어가야 하면 null이다. */
	public FollowUp decide(SessionTurn answered, GradingResult result) {
		if (!canFollowUp(answered)) {
			return null;
		}
		if (result.nextAction() == NextAction.CLARIFY) {
			return new FollowUp(answered.getQuestionText(), answered.getExpectedPoints(), answered.getCommonMistakes());
		}
		boolean asksAgain = result.nextAction() == NextAction.FOLLOW_UP
				|| result.nextAction() == NextAction.EXPLAIN_AND_RECHECK;
		if (asksAgain && hasText(result.followUpQuestion()) && !isEmpty(result.followUpExpectedPoints())) {
			return new FollowUp(result.followUpQuestion(), result.followUpExpectedPoints(), List.of());
		}
		return null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static boolean isEmpty(List<String> values) {
		return values == null || values.isEmpty();
	}

	public record FollowUp(String questionText, List<String> expectedPoints, List<String> commonMistakes) {
	}

}
