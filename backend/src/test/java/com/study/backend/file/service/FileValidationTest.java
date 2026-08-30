package com.study.backend.file.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.study.backend.file.exception.FileException;
import com.study.backend.file.mapper.FileMapper;
import com.study.backend.file.model.FileMetaData;

@ExtendWith(MockitoExtension.class)
class FileValidationTest {

    @TempDir Path tempDir;

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
	@DisplayName("JPEG 내용을 HTML 파일명으로 업로드해도 서버는 JPG로 저장한다")
	void createFiles_jpegNamedHtml_usesCanonicalJpgExtension() throws Exception {
		Path uploadDirectory = Files.createDirectories(tempDir.resolve("test"));
		TestFileService uploadService = new TestFileService(fileMapper, tempDir.toString());

		byte[] jpeg = createImageBytes();

		MockMultipartFile file = new MockMultipartFile(
			"file",
			"evil.html",
			"image/jpeg",
			jpeg
		);

		List<Path> savedPaths = uploadService.createFiles(1L, new MockMultipartFile[]{file});

		ArgumentCaptor<FileMetaData> metadataCaptor = ArgumentCaptor.forClass(FileMetaData.class);
		then(fileMapper).should().createFile(metadataCaptor.capture());
		FileMetaData metadata = metadataCaptor.getValue();

		assertThat(metadata.getFileName()).isEqualTo("evil.jpg");
		assertThat(metadata.getExtension()).isEqualTo(".jpg");
		assertThat(savedPaths).singleElement().satisfies(path -> {
			assertThat(path.getParent()).isEqualTo(uploadDirectory);
			assertThat(path.getFileName().toString()).endsWith(".jpg").doesNotEndWith(".html");
			assertThat(path).exists();
		});
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
		given(fileMapper.getFilesByBoardId(1L)).willReturn(existingFiles());
		MockMultipartFile newFile = jpegFile();

		assertThatThrownBy(() -> fileService.validateFileCountForUpdate(1L, null, new MockMultipartFile[]{newFile}))
			.isInstanceOf(FileException.class)
			.hasMessageContaining("5개");
	}

	@Test
	@DisplayName("기존 파일을 삭제한 만큼 새 파일을 추가할 수 있다")
	void validateFileCountForUpdate_deleteThenAdd_passes() {
		given(fileMapper.getFilesByBoardId(1L)).willReturn(existingFiles());
		MockMultipartFile newFile = jpegFile();

		assertThatNoException().isThrownBy(() ->
			fileService.validateFileCountForUpdate(1L, new String[]{"1"}, new MockMultipartFile[]{newFile})
		);
	}

	@Test
	@DisplayName("삭제 요청 파일이 게시글 파일이 아니면 예외를 던진다")
	void validateFileCountForUpdate_invalidDeleteFile_throws() {
		given(fileMapper.getFilesByBoardId(1L)).willReturn(existingFiles());

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

	@Test
	@DisplayName("여러 이미지가 모두 정상 해상도이면 전부 저장한다")
	void createFiles_multipleValidImages_savesAllFiles() throws Exception {
		Path uploadDirectory =
			Files.createDirectories(tempDir.resolve("test"));

		TestFileService uploadService =
			new TestFileService(fileMapper, tempDir.toString());

		MockMultipartFile firstImage = new MockMultipartFile(
			"file",
			"first.png",
			"image/png",
			createPngBytes(10, 10)
		);

		MockMultipartFile secondImage = new MockMultipartFile(
			"file",
			"second.png",
			"image/png",
			createPngBytes(20, 20)
		);

		List<Path> savedPaths = uploadService.createFiles(
			1L,
			new MockMultipartFile[]{firstImage, secondImage}
		);

		assertThat(savedPaths).hasSize(2);
		assertThat(savedPaths).allSatisfy(path ->
			assertThat(path).exists()
		);

		then(fileMapper)
			.should(times(2))
			.createFile(any(FileMetaData.class));

		assertThat(uploadDirectory).isDirectory();
	}

	@Test
	@DisplayName("두 번째 이미지의 해상도가 초과되면 앞서 저장한 파일까지 정리한다")
	void createFiles_secondImageTooWide_deletesAllWrittenFiles()
		throws Exception {

		Path uploadDirectory =
			Files.createDirectories(tempDir.resolve("test"));

		TestFileService uploadService =
			new TestFileService(fileMapper, tempDir.toString());

		MockMultipartFile validImage = new MockMultipartFile(
			"file",
			"valid.png",
			"image/png",
			createPngBytes(10, 10)
		);

		MockMultipartFile oversizedImage = new MockMultipartFile(
			"file",
			"oversized.png",
			"image/png",
			createPngBytes(8_001, 1)
		);

		assertThatThrownBy(() ->
			uploadService.createFiles(
				1L,
				new MockMultipartFile[]{
					validImage,
					oversizedImage
				}
			)
		)
			.isInstanceOf(FileException.class)
			.hasMessage("이미지 해상도가 너무 큽니다");

		try (var remainingFiles = Files.list(uploadDirectory)) {
			assertThat(remainingFiles.toList()).isEmpty();
		}
	}

	private MockMultipartFile jpegFile() {
		byte[] jpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
		return new MockMultipartFile("file", "new.jpg", "image/jpeg", jpegHeader);
	}

	private List<FileMetaData> existingFiles() {
		return java.util.stream.LongStream.rangeClosed(1, 5)
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

	private byte[] createPngBytes(int width, int height)
		throws Exception {

		BufferedImage image = new BufferedImage(
			width,
			height,
			BufferedImage.TYPE_INT_RGB
		);

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);

		return output.toByteArray();
	}

	private byte[] createImageBytes()
		throws IOException {

		BufferedImage image =
			new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "jpeg", output);

		return output.toByteArray();
	}

    // ── 테스트용 구체 구현 ────────────────────────────────────────────────

    static class TestFileService extends AbstractFileService {
		private final String storePath;

        public TestFileService(FileMapper fileMapper) {
			this(fileMapper, "/store");
		}

		public TestFileService(FileMapper fileMapper, String storePath) {
            super(fileMapper, event -> {});
			this.storePath = storePath;
        }

        @Override protected String getPath() { return "/test/"; }
        @Override protected String getDeletedPath() { return "/test/deleted/"; }
        @Override protected Long getFileMaxSize() { return 2L * 1024 * 1024; }
        @Override protected Set<String> getFileTypes() { return Set.of("jpeg", "png", "gif"); }
        @Override protected int getMaxFileCount() { return 5; }

        @Override
        public String resolveAbsolutePath(String path) {
			return joinStorePath(storePath, path);
        }
    }
}
