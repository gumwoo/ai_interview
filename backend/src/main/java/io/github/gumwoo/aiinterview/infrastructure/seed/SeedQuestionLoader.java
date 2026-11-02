package io.github.gumwoo.aiinterview.infrastructure.seed;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;

import io.github.gumwoo.aiinterview.domain.question.QuestionBankItem;
import io.github.gumwoo.aiinterview.domain.question.QuestionBankItem.QuestionOrigin;
import io.github.gumwoo.aiinterview.domain.question.QuestionBankItem.ReferencePoints;
import io.github.gumwoo.aiinterview.infrastructure.persistence.QuestionBankRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code classpath:seed/*.json}의 시드 질문을 질문 은행에 넣는다. 같은 질문이 이미 있으면 건너뛴다.
 */
@Component
class SeedQuestionLoader implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SeedQuestionLoader.class);
	private static final String SEED_PATTERN = "classpath*:seed/*.json";

	private final QuestionBankRepository repository;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	SeedQuestionLoader(QuestionBankRepository repository, JsonMapper jsonMapper, Clock clock) {
		this.repository = repository;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		int added = 0;
		for (Resource resource : new PathMatchingResourcePatternResolver().getResources(SEED_PATTERN)) {
			try (InputStream input = resource.getInputStream()) {
				List<SeedQuestion> seeds = jsonMapper.readValue(input, new TypeReference<>() {
				});
				for (SeedQuestion seed : seeds) {
					added += save(seed) ? 1 : 0;
				}
			}
		}
		if (added > 0) {
			log.info("시드 질문 {}개를 추가했습니다", added);
		}
	}

	private boolean save(SeedQuestion seed) {
		if (repository.existsByQuestionText(seed.question())) {
			return false;
		}
		repository.save(new QuestionBankItem(seed.topic(), seed.conceptKey(), seed.difficulty(), seed.question(),
				new ReferencePoints(seed.expectedPoints(), seed.commonMistakes()), QuestionOrigin.SEED, seed.sourceRef(),
				seed.sourceCommit(), clock.instant()));
		return true;
	}

	record SeedQuestion(String topic, String conceptKey, String difficulty, String question,
			List<String> expectedPoints, List<String> commonMistakes, String sourceRef, String sourceCommit) {
	}

}
