package com.study.backend.board.strategy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.study.backend.board.assembler.InquiryResponseAssembler;
import com.study.backend.board.model.BoardType;
import com.study.backend.board.model.Search;
import com.study.backend.board.service.InquiryService;
import com.study.backend.common.exception.AuthorizationException;
import com.study.backend.common.util.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
class InquiryStrategyTest {

	@Mock InquiryService boardService;
	@Mock InquiryResponseAssembler responseAssembler;
	@Mock JwtTokenProvider jwtTokenProvider;

	@InjectMocks InquiryStrategy inquiryStrategy;

	@Test
	@DisplayName("비로그인 상태에서 내 문의만 조회하면 인증 예외를 던진다")
	void searchPostList_onlyMineWithoutMember_throws() {
		Search search = Search.builder().onlyMine(true).build();

		assertThatThrownBy(() -> inquiryStrategy.searchPostList(search))
			.isInstanceOf(AuthorizationException.class)
			.hasMessage("내 문의만 조회하려면 로그인이 필요합니다.");

		then(boardService).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("비로그인 상태에서 내 문의 개수를 조회해도 인증 예외를 던진다")
	void getPostCount_onlyMineWithoutMember_throws() {
		Search search = Search.builder().onlyMine(true).build();

		assertThatThrownBy(() -> inquiryStrategy.getPostCount(search))
			.isInstanceOf(AuthorizationException.class)
			.hasMessage("내 문의만 조회하려면 로그인이 필요합니다.");

		then(boardService).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("로그인 상태에서는 내 문의 목록과 개수 조회를 허용한다")
	void onlyMineWithMember_delegatesToService() {
		Search search = Search.builder().onlyMine(true).memberId(1L).build();
		given(boardService.searchPostList(search, BoardType.INQUIRIES.id())).willReturn(List.of());
		given(boardService.getPostCountByCriteria(search, BoardType.INQUIRIES.id())).willReturn(0);

		assertThat(inquiryStrategy.searchPostList(search)).isEmpty();
		assertThat(inquiryStrategy.getPostCount(search)).isZero();

		then(boardService).should().searchPostList(search, BoardType.INQUIRIES.id());
		then(boardService).should().getPostCountByCriteria(search, BoardType.INQUIRIES.id());
	}

	@Test
	@DisplayName("비로그인 상태여도 전체 문의 조회는 허용한다")
	void allPostsWithoutMember_delegatesToService() {
		Search search = Search.builder().onlyMine(false).build();
		given(boardService.searchPostList(search, BoardType.INQUIRIES.id())).willReturn(List.of());

		assertThat(inquiryStrategy.searchPostList(search)).isEmpty();

		then(boardService).should().searchPostList(search, BoardType.INQUIRIES.id());
	}
}
