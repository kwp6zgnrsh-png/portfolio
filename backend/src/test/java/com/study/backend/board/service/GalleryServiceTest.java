package com.study.backend.board.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.exception.BoardConflictException;
import com.study.backend.board.exception.BoardNotFoundException;
import com.study.backend.board.exception.BoardPermissionDeniedException;
import com.study.backend.board.mapper.GalleryMapper;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.category.model.CategoryType;
import com.study.backend.category.service.CategoryService;
import com.study.backend.file.cleanup.service.UploadCleanupService;
import com.study.backend.file.exception.FileException;
import com.study.backend.file.model.FileMetaData;
import com.study.backend.file.service.FileService;
import com.study.backend.file.service.FileServiceFactory;
import com.study.backend.thumbnail.model.ThumbnailMetaData;
import com.study.backend.thumbnail.service.ThumbnailService;

@ExtendWith(MockitoExtension.class)
class GalleryServiceTest {

	@TempDir Path tempDir;

    @Mock GalleryMapper galleryMapper;
    @Mock FileServiceFactory fileServiceFactory;
    @Mock FileService fileService;
    @Mock ThumbnailService thumbnailService;
    @Mock ApplicationEventPublisher eventPublisher;
	@Mock CategoryService categoryService;
	@Mock UploadCleanupService uploadCleanupService;

    @InjectMocks GalleryService galleryService;

	@Test
	@DisplayName("갤러리 등록 전에 회원용 카테고리인지 검증한다")
	void createPostWithFilesAndThumbnail_validatesMemberCategory() {
		Board board = Board.builder().id(1L).categoryId(1L).build();
		org.springframework.web.multipart.MultipartFile[] files = {
			mock(org.springframework.web.multipart.MultipartFile.class)
		};
		given(fileServiceFactory.getFileService(BoardType.GALLERIES)).willReturn(fileService);
		given(fileService.getFirstFileByBoardId(1L)).willReturn(fileMetaData());

		galleryService.createPostWithFilesAndThumbnail(board, BoardType.GALLERIES.id(), 1L, files);

		then(categoryService).should().validateCategory(1L, CategoryType.MEMBER);
		then(galleryMapper).should().createPost(board, BoardType.GALLERIES.id(), 1L);
	}

    // ── createFilesAndThumbnail ─────────────────────────────────────────

    @Test
    @DisplayName("파일 저장 후 첫 번째 파일이 없으면 FileException을 던진다")
    void createFilesAndThumbnail_noFirstFile_throws() {
		given(fileServiceFactory.getFileService(BoardType.GALLERIES)).willReturn(fileService);
        given(fileService.getFirstFileByBoardId(1L)).willReturn(null);

        assertThatThrownBy(() -> galleryService.createFilesAndThumbnail(1L, null))
            .isInstanceOf(FileException.class);
    }

    @Test
    @DisplayName("파일 저장 성공 시 썸네일이 생성된다")
    void createFilesAndThumbnail_success_thumbnailSaved() {
        FileMetaData file = fileMetaData();
		given(fileServiceFactory.getFileService(BoardType.GALLERIES)).willReturn(fileService);
        given(fileService.getFirstFileByBoardId(1L)).willReturn(file);

        galleryService.createFilesAndThumbnail(1L, null);

        then(thumbnailService).should().saveThumbnail(any(), eq(1L));
    }

    // ── updatePost ──────────────────────────────────────────────────────

    @Test
    @DisplayName("게시글이 존재하지 않으면 수정 시 예외를 던진다")
    void updatePost_boardNotFound_throws() {
        BoardUpdateRequest update = boardUpdate();
        given(galleryMapper.getPostById(1L)).willReturn(null);

        assertThatThrownBy(() -> galleryService.updatePost(1L, update, 1L, null))
            .isInstanceOf(BoardNotFoundException.class);
    }

    @Test
    @DisplayName("작성자가 아니면 수정 시 예외를 던진다")
    void updatePost_notOwner_throws() {
        BoardUpdateRequest update = boardUpdate();
        given(galleryMapper.getPostById(1L)).willReturn(board(2L));

        assertThatThrownBy(() -> galleryService.updatePost(1L, update, 1L, null))
            .isInstanceOf(BoardPermissionDeniedException.class);
    }

	@Test
	@DisplayName("수정 후 첫 번째 파일이 없으면 FileException을 던진다")
	void updatePost_noFirstFile_throws() {
		BoardUpdateRequest update = boardUpdate();

		MultipartFile mockFile = mock(MultipartFile.class);
		MultipartFile[] files = {mockFile};

		given(mockFile.isEmpty()).willReturn(false);
		given(galleryMapper.getPostById(1L)).willReturn(board(1L));
		given(fileServiceFactory.getFileService(BoardType.GALLERIES)).willReturn(fileService);
		given(thumbnailService.getThumbnailByBoardId(1L)).willReturn(null);
		given(fileService.createFiles(1L, files)).willReturn(List.of());
		given(fileService.getFirstFileByBoardId(1L)).willReturn(null);

		assertThatThrownBy(
			() -> galleryService.updatePost(1L, update, 1L, files)
		)
			.isInstanceOf(FileException.class)
			.hasMessage("파일 저장에 실패했습니다.");
	}

	@Test
	@DisplayName("새 파일이 추가되면 썸네일이 갱신된다")
	void updatePost_success_thumbnailUpdated() {
		BoardUpdateRequest update = boardUpdate();
		FileMetaData firstFile = fileMetaData();

		MultipartFile mockFile = mock(MultipartFile.class);
		MultipartFile[] files = {mockFile};

		given(mockFile.isEmpty()).willReturn(false);
		given(galleryMapper.getPostById(1L)).willReturn(board(1L));
		given(fileServiceFactory.getFileService(BoardType.GALLERIES)).willReturn(fileService);
		given(thumbnailService.getThumbnailByBoardId(1L)).willReturn(null);
		given(fileService.createFiles(1L, files)).willReturn(List.of());
		given(fileService.getFirstFileByBoardId(1L)).willReturn(firstFile);
		given(galleryMapper.updatePost(1L, update, 1L)).willReturn(1);

		galleryService.updatePost(1L, update, 1L, files);

		then(fileService).should().createFiles(1L, files);
		then(thumbnailService).should().saveThumbnail(any(), eq(1L));
		then(galleryMapper).should().updatePost(1L, update, 1L);
	}

	@Test
	@DisplayName("새 썸네일 경로가 기존 썸네일 경로와 같으면 물리 삭제 이벤트를 발행하지 않는다")
	void updatePost_sameThumbnailPath_doesNotPublishDeleteEvent() {
		BoardUpdateRequest update = boardUpdate();
		FileMetaData file = fileMetaData();
		Path sameThumbnailPath = Path.of("/store/thumbnail/stored.jpeg");
		ReflectionTestUtils.setField(galleryService, "storePath", "/store/");

		MultipartFile mockFile = mock(MultipartFile.class);
		MultipartFile[] files = {mockFile};

		given(mockFile.isEmpty()).willReturn(false);
		given(fileService.createFiles(1L, files)).willReturn(List.of());
		given(galleryMapper.getPostById(1L)).willReturn(board(1L));
		given(fileServiceFactory.getFileService(BoardType.GALLERIES)).willReturn(fileService);
		given(thumbnailService.getThumbnailByBoardId(1L)).willReturn(thumbnailMetaData());
		given(fileService.getFirstFileByBoardId(1L)).willReturn(file);
		given(thumbnailService.saveThumbnail(any(), eq(1L))).willReturn(sameThumbnailPath);
		given(galleryMapper.updatePost(1L, update, 1L)).willReturn(1);

		galleryService.updatePost(1L, update, 1L, files);

		then(eventPublisher).should(never()).publishEvent(any());
	}

	@Test
	@DisplayName("썸네일 생성 후 수정 실패 시 예외를 그대로 전파한다")
	void updatePost_failureAfterThumbnail_propagatesException() {
		BoardUpdateRequest update = boardUpdate();

		org.springframework.web.multipart.MultipartFile mockFile =
			mock(org.springframework.web.multipart.MultipartFile.class);

		org.springframework.web.multipart.MultipartFile[] files = {mockFile};

		Path createdThumbnail = tempDir.resolve("created.jpeg");
		RuntimeException failure = new RuntimeException("update failed");

		given(mockFile.isEmpty()).willReturn(false);
		given(galleryMapper.getPostById(1L)).willReturn(board(1L));
		given(fileServiceFactory.getFileService(BoardType.GALLERIES))
			.willReturn(fileService);
		given(fileService.getFirstFileByBoardId(1L))
			.willReturn(fileMetaData());
		given(thumbnailService.saveThumbnail(any(), eq(1L)))
			.willReturn(createdThumbnail);

		willThrow(failure)
			.given(galleryMapper)
			.updatePost(1L, update, 1L);

		assertThatThrownBy(
			() -> galleryService.updatePost(1L, update, 1L, files)
		).isSameAs(failure);

		then(fileService).should().createFiles(1L, files);
		then(thumbnailService).should().saveThumbnail(any(), eq(1L));
	}

    // ── deletePost ──────────────────────────────────────────────────────

    @Test
    @DisplayName("게시글이 존재하지 않으면 삭제 시 예외를 던진다")
    void deletePost_boardNotFound_throws() {
        given(galleryMapper.getPostById(1L)).willReturn(null);

        assertThatThrownBy(() -> galleryService.deletePost(1L, 1L))
            .isInstanceOf(BoardNotFoundException.class);
    }

    @Test
    @DisplayName("작성자가 아니면 삭제 시 예외를 던진다")
    void deletePost_notOwner_throws() {
        given(galleryMapper.getPostById(1L)).willReturn(board(2L));

        assertThatThrownBy(() -> galleryService.deletePost(1L, 1L))
            .isInstanceOf(BoardPermissionDeniedException.class);
    }

    @Test
    @DisplayName("작성자 본인이면 삭제 시 파일과 썸네일이 함께 삭제된다")
    void deletePost_owner_filesAndThumbnailDeleted() {
		given(galleryMapper.getPostById(1L)).willReturn(board(1L));
		given(fileServiceFactory.getFileService(BoardType.GALLERIES)).willReturn(fileService);
        given(thumbnailService.getThumbnailByBoardId(1L)).willReturn(null);
		given(galleryMapper.deletePost(1L, 1L)).willReturn(1);

        galleryService.deletePost(1L, 1L);

        then(galleryMapper).should().deletePost(1L, 1L);
        then(fileService).should().deleteAllFilesByBoardId(1L);
        then(thumbnailService).should().deleteThumbnail(1L);
    }

	@Test
	@DisplayName("오래된 버전으로 수정하면 충돌 예외를 던진다")
	void updatePost_staleVersion_throwsConflict() {
		BoardUpdateRequest update = boardUpdate();

		given(galleryMapper.getPostById(1L)).willReturn(board(1L));
		given(galleryMapper.updatePost(1L, update, 1L)).willReturn(0);

		assertThatThrownBy(() -> galleryService.updatePost(1L, update, 1L, null))
			.isInstanceOf(BoardConflictException.class)
			.hasMessage("게시글 상태가 변경되어 수정할 수 없습니다.");

		then(fileServiceFactory).shouldHaveNoInteractions();
		then(thumbnailService).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("파일 변경 없이 수정하면 게시글만 수정하고 썸네일은 유지한다")
	void updatePost_withoutFileChanges_updatesBoardOnly() {
		BoardUpdateRequest update = boardUpdate();

		given(galleryMapper.getPostById(1L)).willReturn(board(1L));

		given(galleryMapper.updatePost(1L, update, 1L)).willReturn(1);

		galleryService.updatePost(
			1L,
			update,
			1L,
			null
		);

		then(galleryMapper).should().updatePost(1L, update, 1L);

		then(fileServiceFactory).shouldHaveNoInteractions();

		then(thumbnailService).shouldHaveNoInteractions();
	}

    // ── helpers ─────────────────────────────────────────────────────────

	private Board board(Long memberId) {
		return Board.builder().id(1L).memberId(memberId).boardTypeId(BoardType.GALLERIES.id()).build();
	}

	private BoardUpdateRequest boardUpdate() {
		return BoardUpdateRequest.builder()
			.title("제목")
			.content("내용")
			.categoryId(1L)
			.version(0)
			.build();
	}

    private FileMetaData fileMetaData() {
        return FileMetaData.builder()
            .fileName("test.png").storeName("stored").path("/upload/").extension(".png").build();
    }

	private ThumbnailMetaData thumbnailMetaData() {
		return ThumbnailMetaData.builder()
			.fileName("test.png")
			.storeName("stored")
			.path("thumbnail/")
			.extension(".jpeg")
			.build();
	}
}
