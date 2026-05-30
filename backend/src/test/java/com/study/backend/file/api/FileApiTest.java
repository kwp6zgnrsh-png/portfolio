package com.study.backend.file.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.study.backend.common.interceptor.JwtAuthInterceptor;
import com.study.backend.common.interceptor.LoginRateLimitInterceptor;
import com.study.backend.common.util.JwtTokenProvider;
import com.study.backend.file.exception.FileNotFoundException;
import com.study.backend.file.service.FileService;
import com.study.backend.file.service.FileServiceFactory;

@WebMvcTest(FileApi.class)
class FileApiTest {

	@Autowired
	MockMvc mockMvc;

	@MockBean
	FileServiceFactory fileServiceFactory;

	@MockBean
	JwtAuthInterceptor jwtAuthInterceptor;

	@MockBean
	LoginRateLimitInterceptor loginRateLimitInterceptor;

	@MockBean
	JwtTokenProvider jwtTokenProvider;

	private FileService fileService;

	@BeforeEach
	void setUp() throws Exception {
		fileService = Mockito.mock(FileService.class);
		given(fileServiceFactory.getFileService("boards")).willReturn(fileService);
		given(jwtAuthInterceptor.preHandle(any(), any(), any())).willReturn(true);
		given(loginRateLimitInterceptor.preHandle(any(), any(), any())).willReturn(true);
	}

	@Test
	@DisplayName("게시판 타입과 맞지 않는 파일은 404를 반환한다")
	void fileDownload_mismatchedBoardType_returns404() throws Exception {
		given(fileService.getDownloadFile(1L, 2L)).willThrow(new FileNotFoundException("존재하지 않는 파일입니다."));

		mockMvc.perform(get("/api/boards/files/1"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 파일입니다."));

		then(fileService).should().getDownloadFile(1L, 2L);
	}
}
