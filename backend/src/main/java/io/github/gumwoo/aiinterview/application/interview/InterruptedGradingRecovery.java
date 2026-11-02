package io.github.gumwoo.aiinterview.application.interview;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 서버는 한 대뿐이므로, 기동 시점에 채점 중으로 남은 턴은 이전 프로세스가 끝내지 못한 것이다.
 * 실패로 되돌려 앱이 같은 요청을 다시 보낼 수 있게 한다.
 */
@Component
class InterruptedGradingRecovery implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(InterruptedGradingRecovery.class);

	private final InterviewStore store;

	InterruptedGradingRecovery(InterviewStore store) {
		this.store = store;
	}

	@Override
	public void run(ApplicationArguments args) {
		int recovered = store.failInterruptedGradings();
		if (recovered > 0) {
			log.warn("중단된 채점 {}건을 재시도 가능 상태로 되돌렸습니다", recovered);
		}
	}

}
