package com.study.backend.file.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.study.backend.file.exception.FileException;
import com.study.backend.file.mapper.FileMapper;
import com.study.backend.file.model.FileMetaData;

@ExtendWith(MockitoExtension.class)
class FileValidationTest {

    @Mock FileMapper fileMapper;

    private TestFileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new TestFileService(fileMapper);
    }

    // ── magic bytes ──────────────────────────────────────────────────────

    @Test
    @DisplayName("올바른 JPEG 파일은 검증을 통과한다")
    void validateFiles_validJpeg_passes() {
        byte[] jpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegHeader);

        assertThatNoException().isThrownBy(() -> fileService.validateFiles(new MockMultipartFile[]{file}));
    }

    @Test
    @DisplayName("올바른 PNG 파일은 검증을 통과한다")
    void validateFiles_validPng_passes() {
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", pngHeader);

        assertThatNoException().isThrownBy(() -> fileService.validateFiles(new MockMultipartFile[]{file}));
    }

    @Test
    @DisplayName("올바른 GIF 파일은 검증을 통과한다")
    void validateFiles_validGif_passes() {
        byte[] gifHeader = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "test.gif", "image/gif", gifHeader);

        assertThatNoException().isThrownBy(() -> fileService.validateFiles(new MockMultipartFile[]{file}));
    }

    @Test
    @DisplayName("Content-Type은 jpeg지만 실제 바이트가 다르면 예외를 던진다 (스푸핑 방지)")
    void validateFiles_spoofedContentType_throws() {
        byte[] fakeJpeg = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
        MockMultipartFile file = new MockMultipartFile("file", "evil.jpg", "image/jpeg", fakeJpeg);

        assertThatThrownBy(() -> fileService.validateFiles(new MockMultipartFile[]{file}))
            .isInstanceOf(FileException.class)
            .hasMessage("파일 형식 오류");
    }

    @Test
    @DisplayName("허용되지 않은 Content-Type이면 예외를 던진다")
    void validateFiles_disallowedContentType_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "script.html", "text/html", "<script>".getBytes());

        assertThatThrownBy(() -> fileService.validateFiles(new MockMultipartFile[]{file}))
            .isInstanceOf(FileException.class);
    }

    @Test
    @DisplayName("파일 크기가 초과되면 예외를 던진다")
    void validateFiles_oversizedFile_throws() {
        byte[] large = new byte[3 * 1024 * 1024]; // 3MB, 허용은 2MB
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", large);

        assertThatThrownBy(() -> fileService.validateFiles(new MockMultipartFile[]{file}))
            .isInstanceOf(FileException.class)
            .hasMessage("파일 용량 초과");
    }

    @Test
    @DisplayName("파일 개수가 초과되면 예외를 던진다")
    void validateFiles_tooManyFiles_throws() {
        byte[] jpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        MockMultipartFile[] files = new MockMultipartFile[6];
        for (int i = 0; i < 6; i++) {
            files[i] = new MockMultipartFile("file" + i, "test" + i + ".jpg", "image/jpeg", jpegHeader);
        }

        assertThatThrownBy(() -> fileService.validateFiles(files))
            .isInstanceOf(FileException.class)
            .hasMessageContaining("5개");
    }

	@Test
	@DisplayName("수정 후 총 파일 개수가 최대 개수를 넘으면 예외를 던진다")
	void validateFileCountForUpdate_finalCountExceedsMax_throws() {
		given(fileMapper.getFilesByBoardId(1L)).willReturn(existingFiles(5));
		MockMultipartFile newFile = jpegFile("new.jpg");

		assertThatThrownBy(() -> fileService.validateFileCountForUpdate(1L, null, new MockMultipartFile[]{newFile}))
			.isInstanceOf(FileException.class)
			.hasMessageContaining("5개");
	}

	@Test
	@DisplayName("기존 파일을 삭제한 만큼 새 파일을 추가할 수 있다")
	void validateFileCountForUpdate_deleteThenAdd_passes() {
		given(fileMapper.getFilesByBoardId(1L)).willReturn(existingFiles(5));
		MockMultipartFile newFile = jpegFile("new.jpg");

		assertThatNoException().isThrownBy(() ->
			fileService.validateFileCountForUpdate(1L, new String[]{"1"}, new MockMultipartFile[]{newFile})
		);
	}

	@Test
	@DisplayName("삭제 요청 파일이 게시글 파일이 아니면 예외를 던진다")
	void validateFileCountForUpdate_invalidDeleteFile_throws() {
		given(fileMapper.getFilesByBoardId(1L)).willReturn(existingFiles(5));

		assertThatThrownBy(() -> fileService.validateFileCountForUpdate(1L, new String[]{"99"}, null))
			.isInstanceOf(FileException.class)
			.hasMessage("파일이 일치하지 않습니다.");
	}


	@Test
	@DisplayName("저장소 경로와 하위 경로는 슬래시를 보정해 조합한다")
	void resolveAbsolutePath_normalizesSlashes() {
		assertThat(fileService.resolveAbsolutePath("gallery"))
			.isEqualTo("/store/gallery/");
		assertThat(fileService.resolveAbsolutePath("/gallery"))
			.isEqualTo("/store/gallery/");
	}

	private MockMultipartFile jpegFile(String fileName) {
		byte[] jpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
		return new MockMultipartFile("file", fileName, "image/jpeg", jpegHeader);
	}

	private List<FileMetaData> existingFiles(int count) {
		return java.util.stream.LongStream.rangeClosed(1, count)
			.mapToObj(id -> FileMetaData.builder()
				.id(id)
				.fileName("file" + id + ".jpg")
				.storeName("stored" + id)
				.extension(".jpg")
				.path("/test/")
				.boardId(1L)
				.build())
			.toList();
	}

    // ── 테스트용 구체 구현 ────────────────────────────────────────────────

    static class TestFileService extends AbstractFileService {

        public TestFileService(FileMapper fileMapper) {
            super(fileMapper, event -> {});
        }

        @Override protected String getPath() { return "/test/"; }
        @Override protected String getDeletedPath() { return "/test/deleted/"; }
        @Override protected Long getFileMaxSize() { return 2L * 1024 * 1024; }
        @Override protected Set<String> getFileTypes() { return Set.of("jpeg", "png", "gif"); }
        @Override protected int getMaxFileCount() { return 5; }

        @Override
        public String resolveAbsolutePath(String path) {
            return joinStorePath("/store", path);
        }
    }
}
