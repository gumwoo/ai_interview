package io.github.gumwoo.aiinterview.application.llm;

/**
 * Claude 호출 실패. 메시지는 사용자에게 보여도 되는 내용만 담는다.
 */
public abstract sealed class LlmException extends RuntimeException {

	protected LlmException(String message, Throwable cause) {
		super(message, cause);
	}

	/** 오늘의 사용량 상한에 도달했다. Claude를 호출하지 않았다. */
	public static final class QuotaExceeded extends LlmException {

		public QuotaExceeded(String message) {
			super(message, null);
		}

	}

	/** 모델이 답변을 거부했다. 채점에서는 판단보류로 처리한다. */
	public static final class Refused extends LlmException {

		public Refused(String message) {
			super(message, null);
		}

	}

	/** 네트워크, 요청 한도, 서버 오류처럼 나중에 다시 시도할 수 있는 실패다. */
	public static final class Unavailable extends LlmException {

		public Unavailable(String message, Throwable cause) {
			super(message, cause);
		}

	}

	/** 인증, 요청 형식, 크레딧 부족처럼 재시도로 풀리지 않는 실패다. */
	public static final class Rejected extends LlmException {

		public Rejected(String message, Throwable cause) {
			super(message, cause);
		}

	}

	/** 응답이 잘렸거나 스키마에 맞지 않는다. */
	public static final class InvalidResponse extends LlmException {

		public InvalidResponse(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
