package io.github.gumwoo.aiinterview.application.interview;

import io.github.gumwoo.aiinterview.domain.session.SessionTurn;
import io.github.gumwoo.aiinterview.infrastructure.persistence.ReviewItemRepository;
import org.springframework.stereotype.Component;

/**
 * 채점 결과로 복습 목록을 고친다. 틀리거나 일부만 맞히면 넣고, 처음부터 다시 물어 맞히면 뺀다.
 * 꼬리질문을 맞힌 것은 방금 들은 설명을 따라 한 것일 수 있어 빼지 않는다.
 */
@Component
public class ReviewScheduler {

	private final ReviewItemRepository reviews;

	public ReviewScheduler(ReviewItemRepository reviews) {
		this.reviews = reviews;
	}

	void record(SessionTurn turn) {
		switch (turn.getVerdict()) {
			case WRONG, PARTIAL -> reviews.add(turn.getConceptKey(), turn.getId());
			case CORRECT -> {
				if (turn.getFollowUpDepth() == 0) {
					reviews.remove(turn.getConceptKey());
				}
			}
			case UNDETERMINED -> {
			}
		}
	}

}
