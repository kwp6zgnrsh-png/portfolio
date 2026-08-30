package com.study.backend.board.service;

import static org.mockito.BDDMockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.study.backend.board.mapper.BoardMapper;
import com.study.backend.board.model.BoardType;
import com.study.backend.board.model.Search;

@ExtendWith(MockitoExtension.class)
class AbstractBoardServiceTest {

	@Mock BoardMapper boardMapper;

	private AbstractBoardService<BoardMapper> boardService;

	@BeforeEach
	void setUp() {
		boardService = new AbstractBoardService<>(boardMapper) {
			@Override
			protected BoardType boardType() {
				return BoardType.BOARDS;
			}
		};
	}

	@ParameterizedTest
	@CsvSource({
		"501, 10",
		"251, 20",
		"101, 50"
	})

	@DisplayName("limit별 최대 페이지까지 목록 조회를 허용한다")
	void searchPostList_maxPage_allowed(int page, int limit) {
		Search search = Search.builder().page(page).limit(limit).build();
		given(boardMapper.searchPostList(search, BoardType.BOARDS.id(), 5000)).willReturn(List.of());

		boardService.searchPostList(search, BoardType.BOARDS.id());

		then(boardMapper).should().searchPostList(search, BoardType.BOARDS.id(), 5000);
	}

	@Test
	@DisplayName("큰 페이지의 offset을 long 범위로 계산한다")
	void searchPostList_largePage_calculatesLongOffset() {
		Search search = Search.builder()
			.page(100_000_000)
			.limit(50)
			.build();

		long expectedOffset = 4_999_999_950L;

		given(boardMapper.searchPostList(
			search,
			BoardType.BOARDS.id(),
			expectedOffset
		)).willReturn(List.of());

		boardService.searchPostList(search, BoardType.BOARDS.id());

		then(boardMapper).should().searchPostList(
			search,
			BoardType.BOARDS.id(),
			expectedOffset
		);
	}

}
