package com.study.backend.board.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.study.backend.board.converter.BoardConverter;
import com.study.backend.board.dto.home.HomeFreeBoard;
import com.study.backend.board.dto.home.HomeGallery;
import com.study.backend.board.dto.home.HomeInquiry;
import com.study.backend.board.dto.home.response.HomeResponse;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.board.strategy.BoardReadableStrategy;
import com.study.backend.board.strategy.BoardStrategyFactory;
import com.study.backend.common.util.BoardQueryRunner;

class HomeApiTest {

	@Test
	@DisplayName("홈의 한 영역이 실패해도 나머지 영역의 결과는 유지된다")
	void home_oneAreaFails_preservesOtherAreas() {
		BoardStrategyFactory factory = mock(BoardStrategyFactory.class);
		BoardConverter converter = mock(BoardConverter.class);
		BoardQueryRunner runner = new BoardQueryRunner(Runnable::run, 3000);

		BoardReadableStrategy notices = mock(BoardReadableStrategy.class);
		BoardReadableStrategy boards = mock(BoardReadableStrategy.class);
		BoardReadableStrategy galleries = mock(BoardReadableStrategy.class);
		BoardReadableStrategy inquiries = mock(BoardReadableStrategy.class);

		when(factory.requireReadableStrategy(BoardType.NOTICES.name()))
			.thenReturn(notices);
		when(factory.requireReadableStrategy(BoardType.BOARDS.name()))
			.thenReturn(boards);
		when(factory.requireReadableStrategy(BoardType.GALLERIES.name()))
			.thenReturn(galleries);
		when(factory.requireReadableStrategy(BoardType.INQUIRIES.name()))
			.thenReturn(inquiries);

		when(notices.getPostList())
			.thenThrow(new IllegalStateException("공지 조회 실패"));

		Board freeBoard = Board.builder().id(1L).build();
		Board gallery = Board.builder().id(2L).build();
		Board inquiry = Board.builder().id(3L).build();

		when(boards.getPostList()).thenReturn(List.of(freeBoard));
		when(galleries.getPostList()).thenReturn(List.of(gallery));
		when(inquiries.getPostList()).thenReturn(List.of(inquiry));

		HomeFreeBoard freeResult = HomeFreeBoard.builder().id(1L).title("자유글").build();
		HomeGallery galleryResult = HomeGallery.builder().id(2L).build();
		HomeInquiry inquiryResult = HomeInquiry.builder().id(3L).title("문의글").build();

		when(converter.convertToHomeFreeBoard(freeBoard))
			.thenReturn(freeResult);
		when(converter.convertToHomeGallery(gallery))
			.thenReturn(galleryResult);
		when(converter.convertToHomeInquiry(inquiry))
			.thenReturn(inquiryResult);

		HomeApi api = new HomeApi(factory, converter, runner);

		HomeResponse result = (HomeResponse) api.home().payload();

		assertThat(result.noticeList()).isEmpty();
		assertThat(result.freeBoardList()).containsExactly(freeResult);
		assertThat(result.galleryList()).containsExactly(galleryResult);
		assertThat(result.inquiryList()).containsExactly(inquiryResult);
	}
}