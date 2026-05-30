package com.study.backend.board.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.nio.file.Files;
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

import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.exception.BoardNotFoundException;
import com.study.backend.board.exception.BoardPermissionDeniedException;
import com.study.backend.board.mapper.GalleryMapper;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
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

    @InjectMocks GalleryService galleryService;

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
		given(galleryMapper.getPostById(1L)).willReturn(board(1L));
		given(fileServiceFactory.getFileService(BoardType.GALLERIES)).willReturn(fileService);
        given(thumbnailService.getThumbnailByBoardId(1L)).willReturn(null);
        given(fileService.getFirstFileByBoardId(1L)).willReturn(null);

        assertThatThrownBy(() -> galleryService.updatePost(1L, update, 1L, null))
            .isInstanceOf(FileException.class);
    }

    @Test
    @DisplayName("수정 성공 시 썸네일이 갱신된다")
    void updatePost_success_thumbnailUpdated() {
        BoardUpdateRequest update = boardUpdate();
		FileMetaData file = fileMetaData();
		given(galleryMapper.getPostById(1L)).willReturn(board(1L));
		given(fileServiceFactory.getFileService(BoardType.GALLERIES)).willReturn(fileService);
        given(thumbnailService.getThumbnailByBoardId(1L)).willReturn(null);
        given(fileService.getFirstFileByBoardId(1L)).willReturn(file);

        galleryService.updatePost(1L, update, 1L, null);

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

		given(galleryMapper.getPostById(1L)).willReturn(board(1L));
		given(fileServiceFactory.getFileService(BoardType.GALLERIES)).willReturn(fileService);
		given(thumbnailService.getThumbnailByBoardId(1L)).willReturn(thumbnailMetaData());
		given(fileService.getFirstFileByBoardId(1L)).willReturn(file);
		given(thumbnailService.saveThumbnail(any(), eq(1L))).willReturn(sameThumbnailPath);

		galleryService.updatePost(1L, update, 1L, null);

		then(eventPublisher).should(never()).publishEvent(any());
	}

	@Test
	@DisplayName("수정 실패 시 새 이미지와 썸네일 물리 파일을 정리한다")
	void updatePost_failureAfterThumbnail_cleanupCreatedFiles() throws Exception {
		BoardUpdateRequest update = boardUpdate();
		org.springframework.web.multipart.MultipartFile mockFile =
			mock(org.springframework.web.multipart.MultipartFile.class);
		org.springframework.web.multipart.MultipartFile[] files = { mockFile };
		FileMetaData file = fileMetaData();
		Path createdFile = Files.writeString(tempDir.resolve("created.png"), "new");
		Path createdThumbnail = Files.writeString(tempDir.resolve("created.jpeg"), "thumb");

		given(galleryMapper.getPostById(1L)).willReturn(board(1L));
		given(fileServiceFactory.getFileService(BoardType.GALLERIES)).willReturn(fileService);
		given(thumbnailService.getThumbnailByBoardId(1L)).willReturn(null);
		given(fileService.createFiles(1L, files)).willReturn(List.of(createdFile));
		given(fileService.getFirstFileByBoardId(1L)).willReturn(file);
		given(thumbnailService.saveThumbnail(any(), eq(1L))).willReturn(createdThumbnail);
		willThrow(new RuntimeException("update failed"))
			.given(galleryMapper).updatePost(1L, update, 1L);

		assertThatThrownBy(() -> galleryService.updatePost(1L, update, 1L, files))
			.isInstanceOf(RuntimeException.class);

		assertThat(Files.exists(createdFile)).isFalse();
		assertThat(Files.exists(createdThumbnail)).isFalse();
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

        galleryService.deletePost(1L, 1L);

        then(galleryMapper).should().deletePost(1L, 1L);
        then(fileService).should().deleteAllFilesByBoardId(1L);
        then(thumbnailService).should().deleteThumbnail(1L);
    }

    // ── helpers ─────────────────────────────────────────────────────────

	private Board board(Long memberId) {
		return Board.builder().id(1L).memberId(memberId).boardTypeId(BoardType.GALLERIES.id()).build();
	}

    private BoardUpdateRequest boardUpdate() {
        return BoardUpdateRequest.builder().build();
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
