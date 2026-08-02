package com.study.backend.board.api;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.backend.board.converter.BoardConverter;
import com.study.backend.board.exception.BoardNotFoundException;
import com.study.backend.board.exception.BoardPermissionDeniedException;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.Page;
import com.study.backend.board.model.Search;
import com.study.backend.board.strategy.BoardDeleteStrategy;
import com.study.backend.board.strategy.BoardReadableStrategy;
import com.study.backend.board.strategy.BoardStrategyFactory;
import com.study.backend.board.strategy.BoardUpdateStrategy;
import com.study.backend.common.exception.AuthorizationException;
import com.study.backend.common.interceptor.JwtAuthInterceptor;
import com.study.backend.common.interceptor.LoginRateLimitInterceptor;
import com.study.backend.common.util.JwtTokenProvider;
import com.study.backend.common.util.Pagination;
import com.study.backend.file.service.FileServiceFactory;

import jakarta.servlet.http.Cookie;

@WebMvcTest(BoardApi.class)
class BoardApiTest {

    private static final Cookie AUTH_COOKIE = new Cookie("token", "valid-token");
    private static final Cookie SECRET_AUTH_COOKIE = new Cookie("secret_token", "valid-secret-token");
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean BoardStrategyFactory boardStrategyFactory;
    @MockBean FileServiceFactory fileServiceFactory;
    @MockBean BoardConverter boardConverter;
    @MockBean Pagination pagination;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean JwtAuthInterceptor jwtAuthInterceptor;
    @MockBean LoginRateLimitInterceptor loginRateLimitInterceptor;

    @MockBean BoardReadableStrategy boardStrategy;
    @MockBean BoardUpdateStrategy updatableBoardStrategy;
    @MockBean BoardDeleteStrategy deletableBoardStrategy;

    @BeforeEach
    void setUp() {
        given(boardConverter.convertToSearch(any())).willReturn(Search.builder()
            .page(1).limit(10).orderByField("createdDate").direction("DESC").build());
    }

    private void allowInterceptors() throws Exception {
        given(jwtAuthInterceptor.preHandle(any(), any(), any())).willReturn(true);
        given(loginRateLimitInterceptor.preHandle(any(), any(), any())).willReturn(true);
    }

    private void givenAuthenticatedMember(Long memberId) {
        given(jwtTokenProvider.getMemberId(any())).willReturn(memberId);
    }

    // ── searchBoardList ─────────────────────────────────────────────────

    @Test
    @DisplayName("게시글 목록 조회 시 200을 반환한다")
    void searchBoardList_success_returns200() throws Exception {
        allowInterceptors();

        given(boardStrategyFactory.requireReadableStrategy("boards")).willReturn(boardStrategy);
        given(boardStrategy.searchPostList(any())).willReturn(List.of());
        given(boardStrategy.getPostCount(any())).willReturn(0);
        given(pagination.pagination(anyInt(), anyInt(), anyInt())).willReturn(Page.builder().build());
        given(boardStrategy.assembleListResponse(anyList(), any())).willReturn(Map.of());

        mockMvc.perform(get("/api/boards"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("성공"));
    }

    @Test
    @DisplayName("게시글 목록 조회 시 잘못된 limit이면 400을 반환한다")
    void searchBoardList_invalidLimit_returns400() throws Exception {
        allowInterceptors();
        mockMvc.perform(get("/api/boards").param("limit", "0"))
            .andExpect(status().isBadRequest());
    }

	@Test
	@DisplayName("존재하지 않는 날짜로 검색하면 400을 반환한다")
	void searchBoardList_invalidDate_returns400() throws Exception {
		allowInterceptors();

		mockMvc.perform(get("/api/boards")
				.param("startDate", "2026-02-30")
				.param("endDate", "2026-03-01"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("검색 시작일과 종료일 중 하나만 입력하면 400을 반환한다")
	void searchBoardList_incompleteDateRange_returns400() throws Exception {
		allowInterceptors();

		mockMvc.perform(get("/api/boards")
				.param("startDate", "2026-07-01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("시작일과 종료일을 함께 입력해 주세요."));
	}

	@Test
	@DisplayName("검색 시작일이 종료일보다 늦으면 400을 반환한다")
	void searchBoardList_reversedDateRange_returns400() throws Exception {
		allowInterceptors();

		mockMvc.perform(get("/api/boards")
				.param("startDate", "2026-07-15")
				.param("endDate", "2026-07-01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("시작일은 종료일보다 늦을 수 없습니다."));
	}

	@Test
	@DisplayName("비로그인 상태에서 내 문의만 조회하면 401을 반환한다")
	void searchBoardList_onlyMineWithoutLogin_returns401() throws Exception {
		allowInterceptors();
		Search search = Search.builder().page(1).limit(10).onlyMine(true).build();
		given(boardConverter.convertToSearch(any())).willReturn(search);
		given(boardStrategyFactory.requireReadableStrategy("inquiries")).willReturn(boardStrategy);
		given(boardStrategy.searchPostList(search))
			.willThrow(new AuthorizationException("내 문의만 조회하려면 로그인이 필요합니다."));

		mockMvc.perform(get("/api/inquiries").param("onlyMine", "true"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("내 문의만 조회하려면 로그인이 필요합니다."));
	}

    // ── boardDetail ─────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 게시글 조회 시 404를 반환한다")
    void boardDetail_notFound_returns404() throws Exception {
        allowInterceptors();
        given(boardStrategyFactory.requireReadableStrategy("boards")).willReturn(boardStrategy);
        given(boardStrategy.getPostById(1L)).willThrow(new BoardNotFoundException("삭제된 게시글입니다."));

        mockMvc.perform(get("/api/boards/1"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("게시글 상세 조회 성공 시 200을 반환한다")
    void boardDetail_success_returns200() throws Exception {
        allowInterceptors();
        Board board = Board.builder().id(1L).build();
        given(boardStrategyFactory.requireReadableStrategy("boards")).willReturn(boardStrategy);
        given(boardStrategy.getPostById(1L)).willReturn(board);
        given(boardStrategy.assembleDetailResponse(any(Board.class), any())).willReturn(Map.of());

        mockMvc.perform(get("/api/boards/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("성공"));
    }

    @Test
    @DisplayName("비밀글 접근 시 비밀번호가 틀리면 403을 반환한다")
    void boardDetail_secretPost_wrongPassword_returns403() throws Exception {
        allowInterceptors();
        Board board = Board.builder().id(1L).memberId(2L).isSecret(true).build();
        given(boardStrategyFactory.requireReadableStrategy("inquiries")).willReturn(boardStrategy);
        given(boardStrategy.getPostById(1L)).willReturn(board);
        willThrow(new BoardPermissionDeniedException("비밀글입니다."))
            .given(boardStrategyFactory).validateAccess(any(), any(), any(), any());

        mockMvc.perform(get("/api/inquiries/1"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("비밀글 올바른 비밀번호로 접근 시 200을 반환한다")
    void boardDetailWithPassword_correctPassword_returns200() throws Exception {
        allowInterceptors();
        Board board = Board.builder().id(1L).memberId(2L).isSecret(true).build();
        given(boardStrategyFactory.requireReadableStrategy("inquiries")).willReturn(boardStrategy);
        given(boardStrategy.getPostById(1L)).willReturn(board);
        given(boardStrategy.assembleDetailResponse(any(Board.class), any())).willReturn(Map.of());

        mockMvc.perform(get("/api/inquiries/1")
                .cookie(SECRET_AUTH_COOKIE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("성공"));
    }

    // ── getUpdateForm ───────────────────────────────────────────────────

    @Test
    @DisplayName("수정 폼 조회 시 게시글이 없으면 404를 반환한다")
    void getUpdateForm_notFound_returns404() throws Exception {
        allowInterceptors();
        given(boardStrategyFactory.requireUpdateStrategy("boards")).willReturn(updatableBoardStrategy);
        given(updatableBoardStrategy.getPostForUpdate(1L, 1L))
            .willThrow(new BoardNotFoundException("이미 삭제된 게시글입니다."));
        givenAuthenticatedMember(1L);

        mockMvc.perform(get("/api/boards/update/1")
                .cookie(AUTH_COOKIE))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("수정 폼 조회 시 작성자가 아니면 403을 반환한다")
    void getUpdateForm_notOwner_returns403() throws Exception {
        allowInterceptors();
        given(boardStrategyFactory.requireUpdateStrategy("boards")).willReturn(updatableBoardStrategy);
        givenAuthenticatedMember(1L);
        given(updatableBoardStrategy.getPostForUpdate(1L, 1L))
            .willThrow(new BoardPermissionDeniedException("수정할 수 없습니다."));

        mockMvc.perform(get("/api/boards/update/1")
                .cookie(AUTH_COOKIE))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("수정 폼 조회 성공 시 200을 반환한다")
    void getUpdateForm_success_returns200() throws Exception {
        allowInterceptors();
        Board board = Board.builder().id(1L).memberId(1L).build();
        given(boardStrategyFactory.requireUpdateStrategy("boards")).willReturn(updatableBoardStrategy);
        givenAuthenticatedMember(1L);
        given(updatableBoardStrategy.getPostForUpdate(1L, 1L)).willReturn(board);
        given(updatableBoardStrategy.assembleUpdateFormResponse(any(Board.class))).willReturn(Map.of());

        mockMvc.perform(get("/api/boards/update/1")
                .cookie(AUTH_COOKIE))
            .andExpect(status().isOk());
    }

    // ── updateBoard ─────────────────────────────────────────────────────

    @Test
    @DisplayName("게시글 수정 성공 시 200을 반환한다")
    void updateBoard_success_returns200() throws Exception {
        allowInterceptors();
        given(boardStrategyFactory.requireUpdateStrategy("boards")).willReturn(updatableBoardStrategy);
        givenAuthenticatedMember(1L);
        willDoNothing().given(updatableBoardStrategy).updatePost(anyLong(), any(), anyLong(), any());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/boards/1")
                .param("title", "수정 제목")
                .param("content", "수정 내용")
                .cookie(AUTH_COOKIE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("수정 완료"));
    }

    // ── removeBoard ─────────────────────────────────────────────────────

    @Test
    @DisplayName("게시글 삭제 성공 시 200을 반환한다")
    void removeBoard_success_returns200() throws Exception {
        allowInterceptors();
        given(boardStrategyFactory.requireDeleteStrategy("boards")).willReturn(deletableBoardStrategy);
        givenAuthenticatedMember(1L);
        willDoNothing().given(deletableBoardStrategy).deletePost(anyLong(), anyLong());

        mockMvc.perform(delete("/api/boards/1")
                .cookie(AUTH_COOKIE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("삭제 완료"));
    }

}
