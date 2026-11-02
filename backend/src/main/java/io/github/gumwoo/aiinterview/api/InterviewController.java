package io.github.gumwoo.aiinterview.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.github.gumwoo.aiinterview.application.interview.InterviewModels.AnswerView;
import io.github.gumwoo.aiinterview.application.interview.InterviewModels.HintView;
import io.github.gumwoo.aiinterview.application.interview.InterviewModels.SessionView;
import io.github.gumwoo.aiinterview.application.interview.InterviewModels.StartSession;
import io.github.gumwoo.aiinterview.application.interview.InterviewModels.SubmitAnswer;
import io.github.gumwoo.aiinterview.application.interview.InterviewModels.SummaryView;
import io.github.gumwoo.aiinterview.application.interview.InterviewOrchestrator;
import io.github.gumwoo.aiinterview.domain.session.InterviewSession.SessionSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class InterviewController {

	private final InterviewOrchestrator orchestrator;

	public InterviewController(InterviewOrchestrator orchestrator) {
		this.orchestrator = orchestrator;
	}

	@PostMapping
	public SessionView start(@Valid @RequestBody StartSessionRequest request) {
		return orchestrator.start(new StartSession(request.source(), request.scheduledDate(), request.topics(),
				request.difficulty(), request.durationMinutes(), request.reviewRatio(), request.clientRequestId()));
	}

	@GetMapping("/{sessionId}")
	public SessionView get(@PathVariable Long sessionId) {
		return orchestrator.get(sessionId);
	}

	@DeleteMapping("/{sessionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long sessionId) {
		orchestrator.delete(sessionId);
	}

	@PostMapping("/{sessionId}/answers")
	public AnswerView answer(@PathVariable Long sessionId, @Valid @RequestBody AnswerRequest request) {
		return orchestrator.submitAnswer(sessionId,
				new SubmitAnswer(request.turnId(), request.recognizedText(), request.clientRequestId()));
	}

	@PostMapping("/{sessionId}/hint")
	public HintView hint(@PathVariable Long sessionId, @Valid @RequestBody HintRequest request) {
		return orchestrator.hint(sessionId, request.turnId());
	}

	@PostMapping("/{sessionId}/end")
	public SummaryView end(@PathVariable Long sessionId) {
		return orchestrator.end(sessionId);
	}

	@GetMapping("/{sessionId}/summary")
	public SummaryView summary(@PathVariable Long sessionId) {
		return orchestrator.summary(sessionId);
	}

	public record StartSessionRequest(@NotNull SessionSource source, @NotNull LocalDate scheduledDate,
			@NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 100) String> topics,
			@NotBlank @Size(max = 50) String difficulty, @Min(1) @Max(120) int durationMinutes,
			@NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal reviewRatio, @NotNull UUID clientRequestId) {
	}

	public record AnswerRequest(@NotNull Long turnId, @NotBlank @Size(max = 5000) String recognizedText,
			@NotNull UUID clientRequestId) {
	}

	public record HintRequest(@NotNull Long turnId) {
	}

}
