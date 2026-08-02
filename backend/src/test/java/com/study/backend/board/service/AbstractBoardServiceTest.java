package com.study.backend.board.service;

import static org.assertj.core.api.Assertions.*;
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

import com.study.backend.board.exception.InvalidBoardRequestException;
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

	@ParameterizedTest
	@CsvSource({
		"502, 10",
		"252, 20",
		"102, 50"
	})
	@DisplayName("limit별 최대 페이지를 초과하면 목록 쿼리를 실행하지 않는다")
	void searchPostList_exceedsMaxPage_throws(int page, int limit) {
		Search search = Search.builder().page(page).limit(limit).build();

		assertThatThrownBy(() -> boardService.searchPostList(search, BoardType.BOARDS.id()))
			.isInstanceOf(InvalidBoardRequestException.class)
			.hasMessage("조회 가능한 페이지 범위를 초과했습니다.");

		then(boardMapper).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("최대 페이지를 초과하면 개수 쿼리도 실행하지 않는다")
	void getPostCountByCriteria_exceedsMaxPage_throws() {
		Search search = Search.builder().page(502).limit(10).build();

		assertThatThrownBy(() -> boardService.getPostCountByCriteria(search, BoardType.BOARDS.id()))
			.isInstanceOf(InvalidBoardRequestException.class)
			.hasMessage("조회 가능한 페이지 범위를 초과했습니다.");

		then(boardMapper).shouldHaveNoInteractions();
	}
}
