package io.github.gumwoo.aiinterview.api;

import java.time.LocalDate;
import java.util.List;

import io.github.gumwoo.aiinterview.application.interview.InterviewModels.HistoryItem;
import io.github.gumwoo.aiinterview.application.interview.InterviewOrchestrator;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

	private final InterviewOrchestrator orchestrator;

	public HistoryController(InterviewOrchestrator orchestrator) {
		this.orchestrator = orchestrator;
	}

	@GetMapping
	public List<HistoryItem> history(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return orchestrator.history(from, to);
	}

}
