package io.github.gumwoo.aiinterview.application.interview;

/**
 * 면접 흐름의 업무 오류. 메시지는 사용자에게 보여도 되는 내용만 담는다.
 */
public class InterviewException extends RuntimeException {

	private final ErrorCode code;

	public InterviewException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}

	public enum ErrorCode {

		SESSION_NOT_FOUND, TURN_NOT_FOUND, SESSION_COMPLETED, SCHEDULED_SESSION_EXISTS, DAILY_SESSION_LIMIT,
		GRADING_IN_PROGRESS, TURN_ALREADY_ANSWERED, REQUEST_ID_ALREADY_USED

	}

}
