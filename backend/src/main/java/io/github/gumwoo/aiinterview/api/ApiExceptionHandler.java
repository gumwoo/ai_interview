package io.github.gumwoo.aiinterview.api;

import io.github.gumwoo.aiinterview.application.interview.InterviewException;
import io.github.gumwoo.aiinterview.application.llm.LlmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(InterviewException.class)
	ResponseEntity<ErrorResponse> handle(InterviewException ex) {
		HttpStatus status = switch (ex.getCode()) {
			case SESSION_NOT_FOUND, TURN_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case DAILY_SESSION_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
			case SESSION_COMPLETED, SCHEDULED_SESSION_EXISTS, GRADING_IN_PROGRESS, TURN_ALREADY_ANSWERED,
					REQUEST_ID_ALREADY_USED ->
				HttpStatus.CONFLICT;
		};
		return error(status, ex.getCode().name(), ex.getMessage());
	}

	@ExceptionHandler(LlmException.class)
	ResponseEntity<ErrorResponse> handle(LlmException ex) {
		log.warn("Claude 호출 실패: {}", ex.getMessage(), ex.getCause());
		if (ex instanceof LlmException.QuotaExceeded) {
			return error(HttpStatus.TOO_MANY_REQUESTS, "LLM_QUOTA_EXCEEDED", ex.getMessage());
		}
		if (ex instanceof LlmException.Unavailable) {
			return error(HttpStatus.SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE", ex.getMessage());
		}
		if (ex instanceof LlmException.Rejected) {
			return error(HttpStatus.BAD_GATEWAY, "LLM_REJECTED", ex.getMessage());
		}
		if (ex instanceof LlmException.Refused) {
			return error(HttpStatus.BAD_GATEWAY, "LLM_REFUSED", ex.getMessage());
		}
		return error(HttpStatus.BAD_GATEWAY, "LLM_INVALID_RESPONSE", "응답을 처리하지 못했습니다");
	}

	@ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
			MethodArgumentTypeMismatchException.class })
	ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 형식이 올바르지 않습니다");
	}

	private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, message));
	}

	public record ErrorResponse(String code, String message) {
	}

}
