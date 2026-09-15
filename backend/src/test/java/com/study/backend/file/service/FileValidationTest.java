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
import java.util.stream.Stream;

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

import com.study.backend.file.cleanup.service.FileCleanupTaskService;
import com.study.backend.file.cleanup.service.FileCleanupWorker;
import com.study.backend.file.cleanup.service.UploadCleanupService;
import com.study.backend.file.exception.FileException;
import com.study.backend.file.mapper.FileMapper;
import com.study.backend.file.model.FileMetaData;

@ExtendWith(MockitoExtension.class)
class FileValidationTest {

	@TempDir
	Path tempDir;

	@Mock
	FileMapper fileMapper;

	private TestFileService fileService;

	@BeforeEach
	void setUp() {
		fileService = new TestFileService(fileMapper);
	}

	// ── 파일 형식 검증 ──────────────────────────────────

	@Test
	@DisplayName("올바른 JPEG 파일은 검증을 통과한다")
	void validateFiles_validJpeg_passes() {
		MockMultipartFile file = jpegFile();

		assertThatNoException().isThrownBy(
			() -> fileService.validateFiles(new MockMultipartFile[]{file})
		);
	}

	@Test
	@DisplayName("올바른 PNG 파일은 검증을 통과한다")
	void validateFiles_validPng_passes() {
		byte[] pngHeader = {
			(byte) 0x89, 0x50, 0x4E, 0x47,
			0x0D, 0x0A, 0x1A, 0x0A
		};

		MockMultipartFile file = new MockMultipartFile(
			"file", "test.png", "image/png", pngHeader
		);

		assertThatNoException().isThrownBy(
			() -> fileService.validateFiles(new MockMultipartFile[]{file})
		);
	}

	@Test
	@DisplayName("올바른 GIF 파일은 검증을 통과한다")
	void validateFiles_validGif_passes() {
		byte[] gifHeader = {
			0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 0
		};

		MockMultipartFile file = new MockMultipartFile(
			"file", "test.gif", "image/gif", gifHeader
		);

		assertThatNoException().isThrownBy(
			() -> fileService.validateFiles(new MockMultipartFile[]{file})
		);
	}

	@Test
	@DisplayName("Content-Type은 jpeg지만 실제 바이트가 다르면 예외를 던진다")
	void validateFiles_spoofedContentType_throws() {
		byte[] fakeJpeg = {
			0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07
		};

		MockMultipartFile file = new MockMultipartFile(
			"file", "evil.jpg", "image/jpeg", fakeJpeg
		);

		assertThatThrownBy(
			() -> fileService.validateFiles(new MockMultipartFile[]{file})
		)
			.isInstanceOf(FileException.class)
			.hasMessage("파일 형식 오류");
	}

	@Test
	@DisplayName("허용되지 않은 Content-Type이면 예외를 던진다")
	void validateFiles_disallowedContentType_throws() {
		MockMultipartFile file = new MockMultipartFile(
			"file",
			"script.html",
			"text/html",
			"<script>".getBytes()
		);

		assertThatThrownBy(
			() -> fileService.validateFiles(new MockMultipartFile[]{file})
		).isInstanceOf(FileException.class);
	}

	@Test
	@DisplayName("JPEG 내용을 HTML 파일명으로 업로드해도 서버는 JPG로 저장한다")
	void createFiles_jpegNamedHtml_usesCanonicalJpgExtension()
		throws Exception {

		Path uploadDirectory =
			Files.createDirectories(tempDir.resolve("test"));

		TestFileService uploadService =
			new TestFileService(fileMapper, tempDir.toString());

		MockMultipartFile file = new MockMultipartFile(
			"file",
			"evil.html",
			"image/jpeg",
			createImageBytes()
		);

		List<Path> savedPaths = uploadService.createFiles(
			1L,
			new MockMultipartFile[]{file}
		);

		ArgumentCaptor<FileMetaData> metadataCaptor = ArgumentCaptor.forClass(FileMetaData.class);

		then(fileMapper).should().createFile(metadataCaptor.capture());

		FileMetaData metadata = metadataCaptor.getValue();

		assertThat(metadata.getFileName()).isEqualTo("evil.jpg");
		assertThat(metadata.getExtension()).isEqualTo(".jpg");

		assertThat(savedPaths).singleElement().satisfies(path -> {
			assertThat(path.getParent()).isEqualTo(uploadDirectory);
			assertThat(path.getFileName().toString())
				.endsWith(".jpg")
				.doesNotEndWith(".html");
			assertThat(path).exists();
		});
	}

	// ── 용량·개수 검증 ──────────────────────────────────

	@Test
	@DisplayName("파일 크기가 초과되면 예외를 던진다")
	void validateFiles_oversizedFile_throws() {
		byte[] large = new byte[3 * 1024 * 1024];

		MockMultipartFile file = new MockMultipartFile(
			"file", "big.jpg", "image/jpeg", large
		);

		assertThatThrownBy(
			() -> fileService.validateFiles(new MockMultipartFile[]{file})
		)
			.isInstanceOf(FileException.class)
			.hasMessage("파일 용량 초과");
	}

	@Test
	@DisplayName("파일 개수가 초과되면 예외를 던진다")
	void validateFiles_tooManyFiles_throws() {
		MockMultipartFile[] files = new MockMultipartFile[6];

		for (int i = 0; i < files.length; i++) {
			files[i] = jpegFile();
		}

		assertThatThrownBy(() -> fileService.validateFiles(files))
			.isInstanceOf(FileException.class)
			.hasMessageContaining("5개");
	}

	@Test
	@DisplayName("수정 후 총 파일 개수가 최대 개수를 넘으면 예외를 던진다")
	void validateFileCountForUpdate_finalCountExceedsMax_throws() {
		given(fileMapper.getFilesByBoardId(1L))
			.willReturn(existingFiles());

		MockMultipartFile newFile = jpegFile();

		assertThatThrownBy(
			() -> fileService.validateFileCountForUpdate(
				1L,
				null,
				new MockMultipartFile[]{newFile}
			)
		)
			.isInstanceOf(FileException.class)
			.hasMessageContaining("5개");
	}

	@Test
	@DisplayName("기존 파일을 삭제한 만큼 새 파일을 추가할 수 있다")
	void validateFileCountForUpdate_deleteThenAdd_passes() {
		given(fileMapper.getFilesByBoardId(1L))
			.willReturn(existingFiles());

		MockMultipartFile newFile = jpegFile();

		assertThatNoException().isThrownBy(
			() -> fileService.validateFileCountForUpdate(
				1L,
				new String[]{"1"},
				new MockMultipartFile[]{newFile}
			)
		);
	}

	@Test
	@DisplayName("삭제 요청 파일이 게시글 파일이 아니면 예외를 던진다")
	void validateFileCountForUpdate_invalidDeleteFile_throws() {
		given(fileMapper.getFilesByBoardId(1L))
			.willReturn(existingFiles());

		assertThatThrownBy(
			() -> fileService.validateFileCountForUpdate(
				1L,
				new String[]{"99"},
				null
			)
		)
			.isInstanceOf(FileException.class)
			.hasMessage("파일이 일치하지 않습니다.");
	}

	// ── 저장 경로 ───────────────────────────────────────

	@Test
	@DisplayName("저장소 경로와 하위 경로는 슬래시를 보정해 조합한다")
	void resolveAbsolutePath_normalizesSlashes() {
		assertThat(fileService.resolveAbsolutePath("gallery"))
			.isEqualTo("/store/gallery/");

		assertThat(fileService.resolveAbsolutePath("/gallery"))
			.isEqualTo("/store/gallery/");
	}

	// ── 이미지 저장·실패 정리 ────────────────────────────

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
		assertThat(savedPaths).allSatisfy(
			path -> assertThat(path).exists()
		);

		then(fileMapper).should(times(2))
			.createFile(any(FileMetaData.class));

		assertThat(uploadDirectory).isDirectory();
	}

	@Test
	@DisplayName("두 번째 이미지의 해상도가 초과되면 앞서 저장한 파일까지 정리한다")
	void createFiles_secondImageTooWide_deletesAllWrittenFiles()
		throws Exception {

		Path uploadDirectory = Files.createDirectories(tempDir.resolve("test"));

		FileCleanupTaskService taskService = mock(FileCleanupTaskService.class);

		UploadCleanupService realCleanupService =
			new UploadCleanupService(
				new FileCleanupWorker(tempDir.toString()),
				taskService,
				tempDir.toString()
			);

		TestFileService uploadService = new TestFileService(
			fileMapper,
			tempDir.toString(),
			realCleanupService
		);

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

		assertThatThrownBy(
			() -> uploadService.createFiles(
				1L,
				new MockMultipartFile[]{validImage, oversizedImage}
			)
		)
			.isInstanceOf(FileException.class)
			.hasMessage("이미지 해상도가 너무 큽니다");

		try (Stream<Path> remainingFiles = Files.list(uploadDirectory)) {
			assertThat(remainingFiles.toList()).isEmpty();
		}

		// 즉시 삭제에 성공했으므로 재시도 작업을 등록하지 않는다.
		then(taskService).shouldHaveNoInteractions();
	}

	// ── 테스트 도우미 ───────────────────────────────────

	private MockMultipartFile jpegFile() {
		byte[] jpegHeader = {
			(byte) 0xFF, (byte) 0xD8,
			(byte) 0xFF, (byte) 0xE0,
			0, 0, 0, 0
		};

		return new MockMultipartFile(
			"file", "new.jpg", "image/jpeg", jpegHeader
		);
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

	private byte[] createPngBytes(int width, int height) throws IOException {

		BufferedImage image = new BufferedImage(
			width,
			height,
			BufferedImage.TYPE_INT_RGB
		);

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);

		return output.toByteArray();
	}

	private byte[] createImageBytes() throws IOException {
		BufferedImage image = new BufferedImage(
			10,
			10,
			BufferedImage.TYPE_INT_RGB
		);

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "jpeg", output);

		return output.toByteArray();
	}

	// ── 테스트용 구체 구현 ───────────────────────────────

	static class TestFileService extends AbstractFileService {

		private final String storePath;

		public TestFileService(FileMapper fileMapper) {
			this(fileMapper, "/store");
		}

		public TestFileService(FileMapper fileMapper, String storePath) {
			this(
				fileMapper,
				storePath,
				mock(UploadCleanupService.class)
			);
		}

		public TestFileService(
			FileMapper fileMapper,
			String storePath,
			UploadCleanupService uploadCleanupService
		) {
			super(fileMapper, event -> {}, uploadCleanupService);
			this.storePath = storePath;
		}

		@Override
		protected String getPath() {
			return "/test/";
		}

		@Override
		protected String getDeletedPath() {
			return "/test/deleted/";
		}

		@Override
		protected Long getFileMaxSize() {
			return 2L * 1024 * 1024;
		}

		@Override
		protected Set<String> getFileTypes() {
			return Set.of("jpeg", "png", "gif");
		}

		@Override
		protected int getMaxFileCount() {
			return 5;
		}

		@Override
		public String resolveAbsolutePath(String path) {
			return joinStorePath(storePath, path);
		}
	}
}