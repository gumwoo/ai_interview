package io.github.gumwoo.aiinterview.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.Verdict;
import io.github.gumwoo.aiinterview.eval.EvalCases.EvalCase;
import io.github.gumwoo.aiinterview.eval.EvalScorer.CaseRun;
import io.github.gumwoo.aiinterview.eval.EvalScorer.Criteria;
import io.github.gumwoo.aiinterview.eval.EvalScorer.Score;
import org.junit.jupiter.api.Test;

/**
 * 평가셋 파일 형식과 점수 계산을 확인한다. API는 호출하지 않는다.
 */
class EvalFilesTest {

	@Test
	void should_have_valid_cases() {
		List<EvalCase> cases = EvalCases.load().cases();

		assertThat(cases).hasSizeGreaterThanOrEqualTo(30);
		assertThat(cases.stream().map(EvalCase::id)).doesNotHaveDuplicates();
		assertThat(cases).allSatisfy((evalCase) -> {
			assertThat(evalCase.set()).isIn("tune", "holdout");
			assertThat(evalCase.question()).isNotBlank();
			assertThat(evalCase.answer()).isNotBlank();
			assertThat(evalCase.expectedPoints()).isNotEmpty();
			assertThat(evalCase.humanVerdict()).isNotNull();
		});
		assertThat(cases.stream().map(EvalCase::humanVerdict)).contains(Verdict.values());
	}

	@Test
	void should_not_share_concepts_between_tune_and_holdout() {
		Map<String, List<String>> concepts = EvalCases.load()
			.cases()
			.stream()
			.collect(Collectors.groupingBy(EvalCase::set, Collectors.mapping(EvalCase::conceptKey, Collectors.toList())));

		assertThat(concepts.keySet()).containsExactlyInAnyOrder("tune", "holdout");
		Collection<String> holdout = concepts.get("holdout");
		assertThat(concepts.get("tune")).doesNotContainAnyElementsOf(holdout);
	}

	@Test
	void should_score_agreement_by_majority() {
		CaseRun agreed = new CaseRun("a", "tune", Verdict.PARTIAL, List.of(Verdict.PARTIAL, Verdict.PARTIAL, Verdict.CORRECT));
		CaseRun disagreed = new CaseRun("b", "tune", Verdict.PARTIAL, List.of(Verdict.PARTIAL, Verdict.CORRECT, Verdict.CORRECT));

		assertThat(agreed.majorityAgrees()).isTrue();
		assertThat(disagreed.majorityAgrees()).isFalse();
		assertThat(agreed.consistent()).isFalse();
	}

	@Test
	void should_count_failed_run_as_disagreement() {
		CaseRun failed = new CaseRun("a", "tune", Verdict.PARTIAL, java.util.Arrays.asList(null, null, Verdict.PARTIAL));

		assertThat(failed.majorityAgrees()).isFalse();
		assertThat(failed.consistent()).isFalse();
	}

	@Test
	void should_flag_only_critical_confusions() {
		assertThat(new CaseRun("a", "t", Verdict.WRONG, List.of(Verdict.WRONG, Verdict.CORRECT)).hasCriticalError()).isTrue();
		assertThat(new CaseRun("b", "t", Verdict.CORRECT, List.of(Verdict.WRONG)).hasCriticalError()).isTrue();
		assertThat(new CaseRun("c", "t", Verdict.PARTIAL, List.of(Verdict.CORRECT, Verdict.WRONG)).hasCriticalError()).isFalse();
	}

	@Test
	void should_apply_pass_criteria() {
		Criteria criteria = new Criteria(0.8, 0.9, 0);

		assertThat(criteria.passedBy(new Score(10, 0.8, 1, 0), new Score(30, 0.9, 0.9, 0))).isTrue();
		assertThat(criteria.passedBy(new Score(10, 0.7, 1, 0), new Score(30, 0.9, 0.9, 0))).isFalse();
		assertThat(criteria.passedBy(new Score(10, 0.8, 1, 0), new Score(30, 0.9, 0.89, 0))).isFalse();
		assertThat(criteria.passedBy(new Score(10, 0.8, 1, 0), new Score(30, 0.9, 0.9, 1))).isFalse();
	}

}
