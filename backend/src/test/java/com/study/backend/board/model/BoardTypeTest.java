package com.study.backend.board.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoardTypeTest {

	@Test
	@DisplayName("게시판 타입별 정책 메서드는 현재 규칙과 일치한다")
	void boardTypePolicies_matchCurrentRules() {
		assertThat(BoardType.NOTICES.supportsCreate()).isFalse();
		assertThat(BoardType.BOARDS.supportsCreate()).isTrue();
		assertThat(BoardType.GALLERIES.supportsCreate()).isTrue();
		assertThat(BoardType.INQUIRIES.supportsCreate()).isTrue();

		assertThat(BoardType.NOTICES.supportsUpdate()).isFalse();
		assertThat(BoardType.BOARDS.supportsUpdate()).isTrue();
		assertThat(BoardType.GALLERIES.supportsUpdate()).isTrue();
		assertThat(BoardType.INQUIRIES.supportsUpdate()).isTrue();

		assertThat(BoardType.NOTICES.supportsDelete()).isFalse();
		assertThat(BoardType.BOARDS.supportsDelete()).isTrue();
		assertThat(BoardType.GALLERIES.supportsDelete()).isTrue();
		assertThat(BoardType.INQUIRIES.supportsDelete()).isTrue();

		assertThat(BoardType.NOTICES.supportsFile()).isFalse();
		assertThat(BoardType.BOARDS.supportsFile()).isTrue();
		assertThat(BoardType.GALLERIES.supportsFile()).isTrue();
		assertThat(BoardType.INQUIRIES.supportsFile()).isFalse();

		assertThat(BoardType.NOTICES.supportsThumbnail()).isFalse();
		assertThat(BoardType.BOARDS.supportsThumbnail()).isFalse();
		assertThat(BoardType.GALLERIES.supportsThumbnail()).isTrue();
		assertThat(BoardType.INQUIRIES.supportsThumbnail()).isFalse();

		assertThat(BoardType.NOTICES.supportsSecretPost()).isFalse();
		assertThat(BoardType.BOARDS.supportsSecretPost()).isFalse();
		assertThat(BoardType.GALLERIES.supportsSecretPost()).isFalse();
		assertThat(BoardType.INQUIRIES.supportsSecretPost()).isTrue();
	}
}
