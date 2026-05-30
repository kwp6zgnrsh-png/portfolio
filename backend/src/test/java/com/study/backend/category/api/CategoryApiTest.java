package com.study.backend.category.api;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.study.backend.board.exception.BoardTypeException;
import com.study.backend.category.model.Category;
import com.study.backend.category.service.CategoryService;
import com.study.backend.common.interceptor.JwtAuthInterceptor;
import com.study.backend.common.interceptor.LoginRateLimitInterceptor;
import com.study.backend.common.util.JwtTokenProvider;

@WebMvcTest(CategoryApi.class)
class CategoryApiTest {

	@Autowired MockMvc mockMvc;

	@MockBean CategoryService categoryService;
	@MockBean JwtAuthInterceptor jwtAuthInterceptor;
	@MockBean LoginRateLimitInterceptor loginRateLimitInterceptor;
	@MockBean JwtTokenProvider jwtTokenProvider;

	@Test
	@DisplayName("카테고리를 지원하는 게시판은 200을 반환한다")
	void getCategories_supportedBoardType_returns200() throws Exception {
		Category category = new Category(1L, "자유");
		given(categoryService.getCategories("BOARDS")).willReturn(List.of(category));
		given(jwtAuthInterceptor.preHandle(any(), any(), any())).willReturn(true);
		given(loginRateLimitInterceptor.preHandle(any(), any(), any())).willReturn(true);

		mockMvc.perform(get("/api/categories").param("boardType", "BOARDS"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("성공"))
			.andExpect(jsonPath("$.payload[0].name").value("자유"));
	}

	@Test
	@DisplayName("카테고리를 지원하지 않는 게시판은 400을 반환한다")
	void getCategories_unsupportedBoardType_returns400() throws Exception {
		willThrow(new BoardTypeException("카테고리를 지원하지 않는 게시판입니다: INQUIRIES"))
			.given(categoryService).getCategories("INQUIRIES");
		given(jwtAuthInterceptor.preHandle(any(), any(), any())).willReturn(true);
		given(loginRateLimitInterceptor.preHandle(any(), any(), any())).willReturn(true);

		mockMvc.perform(get("/api/categories").param("boardType", "INQUIRIES"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("카테고리를 지원하지 않는 게시판입니다: INQUIRIES"));
	}
}
