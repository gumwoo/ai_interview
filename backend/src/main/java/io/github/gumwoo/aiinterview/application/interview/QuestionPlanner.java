package io.github.gumwoo.aiinterview.application.interview;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import io.github.gumwoo.aiinterview.application.llm.InterviewLlm;
import io.github.gumwoo.aiinterview.domain.question.QuestionBankItem;
import io.github.gumwoo.aiinterview.domain.question.QuestionBankItem.QuestionOrigin;
import io.github.gumwoo.aiinterview.domain.question.QuestionBankItem.ReferencePoints;
import io.github.gumwoo.aiinterview.infrastructure.persistence.QuestionBankRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 다음 질문을 정한다. 질문 은행을 먼저 쓰고, 후보가 없을 때만 Claude로 생성해 저장한다.
 * 트랜잭션을 열지 않으므로 Claude를 기다리는 동안 DB 커넥션을 쥐지 않는다.
 */
@Component
public class QuestionPlanner {

	/** 아직 세션이 없을 때 쓰는, 어떤 턴과도 겹치지 않는 값이다. */
	static final long NO_SESSION = -1L;

	private final QuestionBankRepository repository;
	private final InterviewLlm llm;
	private final Clock clock;

	public QuestionPlanner(QuestionBankRepository repository, InterviewLlm llm, Clock clock) {
		this.repository = repository;
		this.llm = llm;
		this.clock = clock;
	}

	public QuestionBankItem next(List<String> topics, String difficulty, long sessionId, List<String> recentConcepts) {
		Optional<QuestionBankItem> picked = repository.pickNext(topics, difficulty, sessionId);
		return picked.orElseGet(() -> generate(topics, difficulty, sessionId, recentConcepts));
	}

	private QuestionBankItem generate(List<String> topics, String difficulty, long sessionId,
			List<String> recentConcepts) {
		String topic = topics.get(Math.floorMod(recentConcepts.size(), topics.size()));
		InterviewLlm.GeneratedQuestion generated = llm.generateQuestion(
				new InterviewLlm.QuestionRequest(topic, difficulty, recentConcepts),
				sessionId == NO_SESSION ? null : sessionId);
		QuestionBankItem item = new QuestionBankItem(topic, generated.conceptKey(), difficulty, generated.question(),
				new ReferencePoints(generated.expectedPoints(), generated.commonMistakes()), QuestionOrigin.GENERATED,
				null, null, clock.instant());
		try {
			return repository.save(item);
		}
		catch (DataIntegrityViolationException ex) {
			return repository.findByQuestionText(generated.question()).orElseThrow(() -> ex);
		}
	}

}
