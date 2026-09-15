package com.study.backend.board.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.exception.BoardConflictException;
import com.study.backend.board.exception.BoardNotFoundException;
import com.study.backend.board.exception.BoardPermissionDeniedException;
import com.study.backend.board.mapper.FreeBoardMapper;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.category.model.CategoryType;
import com.study.backend.category.service.CategoryService;
import com.study.backend.file.cleanup.service.UploadCleanupService;
import com.study.backend.file.service.FileService;
import com.study.backend.file.service.FileServiceFactory;

@ExtendWith(MockitoExtension.class)
class FreeBoardServiceTest {

	@TempDir Path tempDir;

    @Mock FreeBoardMapper freeBoardMapper;
    @Mock FileServiceFactory fileServiceFactory;
    @Mock FileService fileService;
	@Mock CategoryService categoryService;
	@Mock UploadCleanupService uploadCleanupService;

    @InjectMocks FreeBoardService freeBoardService;

	@Test
	@DisplayName("게시글 등록 전에 회원용 카테고리인지 검증한다")
	void createPost_validatesMemberCategory() {
		Board board = Board.builder().categoryId(1L).build();

		freeBoardService.createPost(board, BoardType.BOARDS.id(), 1L);

		then(categoryService).should().validateCategory(1L, CategoryType.MEMBER);
		then(freeBoardMapper).should().createPost(board, BoardType.BOARDS.id(), 1L);
	}

    // ── updatePost ──────────────────────────────────────────────────────

	@Test
	@DisplayName("게시글이 존재하지 않으면 수정 시 예외를 던진다")
	void updatePost_boardNotFound_throws() {
		BoardUpdateRequest update = boardUpdate();
		given(freeBoardMapper.getPostById(1L)).willReturn(null);

        assertThatThrownBy(() -> freeBoardService.updatePost(1L, update, 1L, null))
			.isInstanceOf(BoardNotFoundException.class);
	}

	@Test
	@DisplayName("게시판 타입이 다르면 존재하지 않는 게시글로 처리한다")
	void getPostById_differentBoardType_throwsNotFound() {
		given(freeBoardMapper.getPostById(1L)).willReturn(board(1L, BoardType.INQUIRIES));

		assertThatThrownBy(() -> freeBoardService.getPostById(1L))
			.isInstanceOf(BoardNotFoundException.class)
			.hasMessage("존재하지 않는 게시글입니다.");
	}

    @Test
    @DisplayName("작성자가 아니면 수정 시 예외를 던진다")
    void updatePost_notOwner_throws() {
        BoardUpdateRequest update = boardUpdate();
        given(freeBoardMapper.getPostById(1L)).willReturn(board(2L));

        assertThatThrownBy(() -> freeBoardService.updatePost(1L, update, 1L, null))
            .isInstanceOf(BoardPermissionDeniedException.class);
    }

	//TODO: 테스트 이름 수정
    @Test
    @DisplayName("작성자 본인이면 수정이 정상 처리된다")
    void updatePost_owner_success() {
		BoardUpdateRequest update = boardUpdate();
		given(freeBoardMapper.getPostById(1L)).willReturn(board(1L));
		then(fileServiceFactory).shouldHaveNoInteractions();
		given(freeBoardMapper.updatePost(1L, update, 1L)).willReturn(1);

        assertThatNoException().isThrownBy(() -> freeBoardService.updatePost(1L, update, 1L, null));
        then(freeBoardMapper).should().updatePost(1L, update, 1L);
    }

    @Test
    @DisplayName("수정 시 새 파일이 있으면 createFiles가 호출된다")
    void updatePost_withFiles_createFilesCalled() {
        BoardUpdateRequest update = boardUpdate();
        org.springframework.web.multipart.MultipartFile mockFile =
            mock(org.springframework.web.multipart.MultipartFile.class);
        org.springframework.web.multipart.MultipartFile[] files = { mockFile };

		given(mockFile.isEmpty()).willReturn(false);

		given(freeBoardMapper.getPostById(1L)).willReturn(board(1L));
		given(fileServiceFactory.getFileService(BoardType.BOARDS)).willReturn(fileService);

		given(freeBoardMapper.updatePost(1L, update, 1L)).willReturn(1);

		freeBoardService.updatePost(1L, update, 1L, files);

        then(fileService).should().createFiles(eq(1L), eq(files));
    }



    // ── deletePost ──────────────────────────────────────────────────────

    @Test
    @DisplayName("게시글이 존재하지 않으면 삭제 시 예외를 던진다")
    void deletePost_boardNotFound_throws() {
        given(freeBoardMapper.getPostById(1L)).willReturn(null);

        assertThatThrownBy(() -> freeBoardService.deletePost(1L, 1L))
            .isInstanceOf(BoardNotFoundException.class);
    }

    @Test
    @DisplayName("작성자가 아니면 삭제 시 예외를 던진다")
    void deletePost_notOwner_throws() {
        given(freeBoardMapper.getPostById(1L)).willReturn(board(2L));

        assertThatThrownBy(() -> freeBoardService.deletePost(1L, 1L))
            .isInstanceOf(BoardPermissionDeniedException.class);
    }

    @Test
    @DisplayName("작성자 본인이면 삭제가 정상 처리된다")
    void deletePost_owner_success() {
		given(freeBoardMapper.getPostById(1L)).willReturn(board(1L));
		given(fileServiceFactory.getFileService(BoardType.BOARDS)).willReturn(fileService);
		given(freeBoardMapper.deletePost(1L, 1L))
			.willReturn(1);
        assertThatNoException().isThrownBy(() -> freeBoardService.deletePost(1L, 1L));
        then(freeBoardMapper).should().deletePost(1L, 1L);
    }

	@Test
	@DisplayName("오래된 버전으로 수정하면 충돌 예외를 던진다")
	void updatePost_staleVersion_throwsConflict() {
		BoardUpdateRequest update = boardUpdate();

		given(freeBoardMapper.getPostById(1L))
			.willReturn(board(1L));
		given(freeBoardMapper.updatePost(1L, update, 1L))
			.willReturn(0);

		assertThatThrownBy(
			() -> freeBoardService.updatePost(1L, update, 1L, null)
		)
			.isInstanceOf(BoardConflictException.class)
			.hasMessage("게시글 상태가 변경되어 수정할 수 없습니다.");
	}

	@Test
	@DisplayName("파일 생성 후 수정 실패 시 예외를 그대로 전파한다")
	void updatePost_failureAfterCreateFiles_propagatesException() {
		BoardUpdateRequest update = boardUpdate();

		org.springframework.web.multipart.MultipartFile mockFile =
			mock(org.springframework.web.multipart.MultipartFile.class);

		org.springframework.web.multipart.MultipartFile[] files = {mockFile};

		RuntimeException failure = new RuntimeException("update failed");

		given(mockFile.isEmpty()).willReturn(false);
		given(freeBoardMapper.getPostById(1L)).willReturn(board(1L));
		given(fileServiceFactory.getFileService(BoardType.BOARDS))
			.willReturn(fileService);

		willThrow(failure)
			.given(freeBoardMapper)
			.updatePost(1L, update, 1L);

		assertThatThrownBy(
			() -> freeBoardService.updatePost(1L, update, 1L, files)
		).isSameAs(failure);

		then(fileService).should().createFiles(1L, files);
	}

    // ── helpers ─────────────────────────────────────────────────────────

	private Board board(Long memberId) {
		return board(memberId, BoardType.BOARDS);
	}

	private Board board(Long memberId, BoardType boardType) {
		return Board.builder().id(1L).memberId(memberId).boardTypeId(boardType.id()).build();
	}

	private BoardUpdateRequest boardUpdate() {
		return BoardUpdateRequest.builder()
			.title("제목")
			.content("내용")
			.categoryId(1L)
			.version(0)
			.build();
	}
}
