package io.github.gumwoo.aiinterview.eval;

import java.nio.file.Path;
import java.util.List;

import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.Verdict;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code eval/grading-cases.json}의 내용.
 */
record EvalCases(String verifiedAgainstRepository, List<EvalCase> cases) {

	static final Path EVAL_DIR = Path.of("..", "eval");

	static EvalCases load() {
		return JsonMapper.builder().build().readValue(EVAL_DIR.resolve("grading-cases.json").toFile(), EvalCases.class);
	}

	record EvalCase(String id, String set, String conceptKey, String question, List<String> expectedPoints,
			List<String> commonMistakes, String answer, Verdict humanVerdict) {
	}

}
