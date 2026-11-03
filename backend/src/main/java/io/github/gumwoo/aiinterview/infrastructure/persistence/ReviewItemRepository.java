package io.github.gumwoo.aiinterview.infrastructure.persistence;

import io.github.gumwoo.aiinterview.domain.review.ReviewItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long> {

	@Modifying
	@Query(value = """
			INSERT INTO review_item (concept_key, source_turn_id)
			VALUES (:conceptKey, :turnId)
			ON CONFLICT (concept_key) DO UPDATE SET source_turn_id = EXCLUDED.source_turn_id
			""", nativeQuery = true)
	void add(@Param("conceptKey") String conceptKey, @Param("turnId") Long turnId);

	@Modifying
	@Query(value = "DELETE FROM review_item WHERE concept_key = :conceptKey", nativeQuery = true)
	void remove(@Param("conceptKey") String conceptKey);

}
