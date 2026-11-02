package io.github.gumwoo.aiinterview.eval;

import java.util.List;
import java.util.Objects;

import io.github.gumwoo.aiinterview.application.llm.InterviewLlm.Verdict;

/**
 * 기준 판정과 AI 판정을 비교한다. 실행 실패는 null 판정으로 기록하고 불일치로 센다.
 */
final class EvalScorer {

	private EvalScorer() {
	}

	static Score score(List<CaseRun> runs) {
		int total = runs.size();
		long agreed = runs.stream().filter(CaseRun::majorityAgrees).count();
		long consistent = runs.stream().filter(CaseRun::consistent).count();
		long critical = runs.stream().filter(CaseRun::hasCriticalError).count();
		return new Score(total, ratio(agreed, total), ratio(consistent, total), critical);
	}

	private static double ratio(long part, int total) {
		return total == 0 ? 0 : (double) part / total;
	}

	record CaseRun(String id, String set, Verdict human, List<Verdict> verdicts) {

		/** 과반 판정이 기준 판정과 같은지 본다. */
		boolean majorityAgrees() {
			long same = verdicts.stream().filter((verdict) -> verdict == human).count();
			return same * 2 > verdicts.size();
		}

		/** 모든 실행의 판정이 같은지 본다. */
		boolean consistent() {
			return verdicts.stream().allMatch(Objects::nonNull) && verdicts.stream().distinct().count() == 1;
		}

		/** 맞은 답을 오답으로, 틀린 답을 정답으로 판정한 실행이 있는지 본다. 잘못 외우게 하는 오류라 따로 센다. */
		boolean hasCriticalError() {
			return verdicts.stream()
				.anyMatch((verdict) -> (human == Verdict.CORRECT && verdict == Verdict.WRONG)
						|| (human == Verdict.WRONG && verdict == Verdict.CORRECT));
		}

	}

	record Score(int total, double agreement, double consistency, long criticalErrors) {
	}

	record Criteria(double minHoldoutAgreement, double minConsistency, long maxCriticalErrors) {

		boolean passedBy(Score holdout, Score all) {
			return holdout.agreement() >= minHoldoutAgreement && all.consistency() >= minConsistency
					&& all.criticalErrors() <= maxCriticalErrors;
		}

	}

}
