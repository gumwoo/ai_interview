package io.github.gumwoo.aiinterview.domain.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 다시 물어야 할 개념 하나. 맞히면 행을 지운다.
 */
@Entity
@Table(name = "review_item")
public class ReviewItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "concept_key", nullable = false)
	private String conceptKey;

	/** 가장 최근에 틀린 턴이다. 그 세션을 지우면 복습 항목도 함께 지워진다. */
	@Column(name = "source_turn_id", nullable = false)
	private Long sourceTurnId;

	protected ReviewItem() {
	}

}
