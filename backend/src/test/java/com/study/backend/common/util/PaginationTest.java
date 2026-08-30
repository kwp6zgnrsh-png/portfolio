package com.study.backend.common.util;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.study.backend.board.model.Page;

class PaginationTest {

	private final Pagination pagination = new Pagination();

	@Test
	@DisplayName("마지막 페이지를 초과하면 실제 마지막 페이지로 보정한다")
	void pagination_overLastPage_normalizesCurrentPage() {
		Page page = pagination.pagination(95, 999, 10);

		assertThat(page.getTotalPages()).isEqualTo(10);
		assertThat(page.getCurrentPage()).isEqualTo(10);
	}

	@Test
	@DisplayName("게시글 개수가 int 최댓값이어도 전체 페이지가 오버플로하지 않는다")
	void pagination_maxPostCount_doesNotOverflow() {
		Page page = pagination.pagination(
			Integer.MAX_VALUE,
			Integer.MAX_VALUE,
			10
		);

		assertThat(page.getTotalPages()).isEqualTo(214_748_365);
		assertThat(page.getCurrentPage()).isEqualTo(214_748_365);
	}
}