package io.github.gumwoo.aiinterview.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import io.github.gumwoo.aiinterview.domain.session.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

	Optional<InterviewSession> findByClientRequestId(UUID clientRequestId);

}
