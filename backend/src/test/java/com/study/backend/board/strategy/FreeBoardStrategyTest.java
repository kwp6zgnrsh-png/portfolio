package com.study.backend.board.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import com.study.backend.board.assembler.FreeBoardResponseAssembler;
import com.study.backend.board.dto.common.BoardCreateResult;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.board.service.FreeBoardService;
import com.study.backend.file.exception.FileException;
import com.study.backend.file.service.FileService;
import com.study.backend.file.service.FileServiceFactory;

@ExtendWith(MockitoExtension.class)
class FreeBoardStrategyTest {

	@Mock FreeBoardService boardService;
	@Mock FreeBoardResponseAssembler responseAssembler;
	@Mock FileServiceFactory fileServiceFactory;
	@Mock FileService fileService;

	private FreeBoardStrategy strategy;

	@BeforeEach
	void setUp() {
		strategy = new FreeBoardStrategy(
			boardService,
			responseAssembler,
			fileServiceFactory
		);
	}

	@Test
	@DisplayName("파일 검증 실패 시 게시글은 등록하고 파일 업로드 실패 결과를 반환한다")
	void createPost_fileValidationFails_returnsPartialSuccess() {
		Board board = Board.builder().id(1L).build();
		MultipartFile[] files = { mock(MultipartFile.class) };

		given(fileServiceFactory.getFileService(BoardType.BOARDS)).willReturn(fileService);
		willThrow(new FileException("파일 형식 오류")).given(fileService).validateFiles(files);

		BoardCreateResult result = strategy.createPost(board, BoardType.BOARDS.id(), 1L, files);

		assertThat(result.fileUploadFailed()).isTrue();
		then(boardService).should().createPost(board, BoardType.BOARDS.id(), 1L);
		then(boardService).should(never()).createFiles(anyLong(), any());
	}
}
