package com.study.backend.file.cleanup;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import com.study.backend.file.cleanup.mapper.OrphanFileMapper;
import com.study.backend.file.cleanup.model.OrphanFileCandidate;
import com.study.backend.file.cleanup.model.OrphanFileCandidate.Reason;
import com.study.backend.file.cleanup.model.StoredFileReference;
import com.study.backend.file.cleanup.service.OrphanFileScanner;

@ExtendWith(MockitoExtension.class)
class OrphanFileScannerTest {

	@TempDir
	Path tempDir;

	@Mock
	OrphanFileMapper mapper;

	private Path storageRoot;
	private OrphanFileScanner scanner;

	@BeforeEach
	void setUp() throws IOException {
		storageRoot = Files.createDirectories(
			tempDir.resolve("storage")
		);

		Files.createDirectories(storageRoot.resolve("free"));
		Files.createDirectories(storageRoot.resolve("gallery"));
		Files.createDirectories(storageRoot.resolve("thumbnail"));

		scanner = new OrphanFileScanner(
			mapper,
			storageRoot.toString(),
			"/free/",
			"/gallery/",
			"/thumbnail/",
			24
		);
	}

	// ── 미참조 파일 ─────────────────────────────────────

	@Test
	@DisplayName("오래된 미참조 파일은 UNREFERENCED 후보로 반환한다")
	void scan_oldUnreferencedFile_returnsCandidate() throws Exception {
		Path file = createOldFile("gallery/orphan.jpeg");
		givenReferences(List.of(), List.of());

		List<OrphanFileCandidate> candidates = scanner.scan();

		assertThat(candidates).hasSize(1);

		OrphanFileCandidate candidate = candidates.get(0);

		assertThat(candidate.relativePath())
			.isEqualTo(relativePath(file));
		assertThat(candidate.reason())
			.isEqualTo(Reason.UNREFERENCED);
		assertThat(candidate.fileSize())
			.isEqualTo(Files.size(file));
		assertThat(candidate.lastModifiedAt())
			.isEqualTo(Files.getLastModifiedTime(file).toInstant());

		// 탐지는 파일을 변경하지 않는다.
		assertThat(file).hasContent("test-data");
	}

	// ── 활성 참조 ──────────────────────────────────────

	@Test
	@DisplayName("사용 중인 원본 파일은 후보에서 제외한다")
	void scan_activeOriginal_excludesFile() throws Exception {
		Path file = createOldFile("free/original.png");

		givenReferences(
			List.of(reference("/free/", "original", ".png", false)),
			List.of()
		);

		assertThat(scanner.scan()).isEmpty();
		assertThat(file).exists();
	}

	@Test
	@DisplayName("사용 중인 썸네일은 후보에서 제외한다")
	void scan_activeThumbnail_excludesFile() throws Exception {
		Path file = createOldFile("thumbnail/thumb.jpeg");

		givenReferences(
			List.of(reference("/thumbnail/", "thumb", ".jpeg", false)),
			List.of()
		);

		assertThat(scanner.scan()).isEmpty();
		assertThat(file).exists();
	}

	// ── 삭제 표시된 원본 ─────────────────────────────────

	@Test
	@DisplayName("삭제 표시된 원본은 DELETED_METADATA 후보로 반환한다")
	void scan_deletedOriginal_returnsRecoveryCandidate() throws Exception {
		Path file = createOldFile("gallery/deleted.jpeg");

		givenReferences(
			List.of(reference("/gallery/", "deleted", ".jpeg", true)),
			List.of()
		);

		List<OrphanFileCandidate> candidates = scanner.scan();

		assertThat(candidates).hasSize(1);
		assertThat(candidates.get(0).relativePath())
			.isEqualTo(relativePath(file));
		assertThat(candidates.get(0).reason())
			.isEqualTo(Reason.DELETED_METADATA);

		assertThat(file).exists();
	}

	// ── 대기 중인 정리 작업 ──────────────────────────────

	@Test
	@DisplayName("대기 작업의 원본과 목적지는 모두 후보에서 제외한다")
	void scan_pendingTaskPaths_excludesSourceAndDestination()
		throws Exception {

		Path source = createOldFile("gallery/source.jpeg");
		Path destination = createOldFile("thumbnail/destination.jpeg");

		givenReferences(
			List.of(),
			List.of(
				relativePath(source),
				relativePath(destination)
			)
		);

		assertThat(scanner.scan()).isEmpty();
		assertThat(source).exists();
		assertThat(destination).exists();
	}

	// ── 최근 파일 ──────────────────────────────────────

	@Test
	@DisplayName("최소 경과 시간보다 최근인 파일은 후보에서 제외한다")
	void scan_recentFile_excludesFile() throws Exception {
		Path file = Files.writeString(
			storageRoot.resolve("gallery/recent.jpeg"),
			"recent-data"
		);

		Files.setLastModifiedTime(
			file,
			FileTime.from(Instant.now())
		);

		givenReferences(List.of(), List.of());

		assertThat(scanner.scan()).isEmpty();
		assertThat(file).hasContent("recent-data");
	}

	// ── DB 조회 실패 ────────────────────────────────────

	@Test
	@DisplayName("메타데이터 조회 실패 시 예외를 전파하고 파일을 보존한다")
	void scan_referenceQueryFails_throws() throws Exception {
		Path file = createOldFile("gallery/orphan.jpeg");

		DataAccessResourceFailureException failure =
			new DataAccessResourceFailureException("DB 조회 실패");

		given(mapper.findAllReferences()).willThrow(failure);

		assertThatThrownBy(() -> scanner.scan())
			.isSameAs(failure);

		then(mapper).should(never()).findPendingTaskPaths();
		assertThat(file).hasContent("test-data");
	}

	@Test
	@DisplayName("대기 작업 조회 실패 시에도 예외를 전파하고 파일을 보존한다")
	void scan_pendingTaskQueryFails_throws() throws Exception {
		Path file = createOldFile("gallery/orphan.jpeg");

		DataAccessResourceFailureException failure =
			new DataAccessResourceFailureException("작업 조회 실패");

		given(mapper.findAllReferences()).willReturn(List.of());
		given(mapper.findPendingTaskPaths()).willThrow(failure);

		assertThatThrownBy(() -> scanner.scan())
			.isSameAs(failure);

		assertThat(file).hasContent("test-data");
	}

	// ── 심볼릭 링크 ─────────────────────────────────────

	@Test
	@DisplayName("폴더 내부의 파일·디렉토리 심볼릭 링크는 후보에서 제외한다")
	void scan_symbolicLinkEntries_skipsLinks() throws Exception {
		Path outsideDirectory = Files.createDirectories(
			tempDir.resolve("outside")
		);

		Path outsideFile = Files.writeString(
			outsideDirectory.resolve("outside.jpeg"),
			"outside-data"
		);

		Files.setLastModifiedTime(
			outsideFile,
			FileTime.from(Instant.now().minus(Duration.ofHours(48)))
		);

		Path fileLink = storageRoot.resolve("gallery/file-link.jpeg");
		Path directoryLink = storageRoot.resolve("gallery/directory-link");

		Files.createSymbolicLink(fileLink, outsideFile.toAbsolutePath());
		Files.createSymbolicLink(
			directoryLink,
			outsideDirectory.toAbsolutePath()
		);

		givenReferences(List.of(), List.of());

		assertThat(scanner.scan()).isEmpty();

		assertThat(Files.isSymbolicLink(fileLink)).isTrue();
		assertThat(Files.isSymbolicLink(directoryLink)).isTrue();
		assertThat(outsideFile).hasContent("outside-data");
	}

	@Test
	@DisplayName("설정된 탐색 폴더 자체가 심볼릭 링크이면 탐지를 중단한다")
	void scan_configuredDirectoryIsSymbolicLink_throws()
		throws Exception {

		Path outsideDirectory = Files.createDirectories(
			tempDir.resolve("outside")
		);

		Path outsideFile = Files.writeString(
			outsideDirectory.resolve("outside.jpeg"),
			"outside-data"
		);

		Path linkedDirectory = storageRoot.resolve("linked-gallery");

		Files.createSymbolicLink(
			linkedDirectory,
			outsideDirectory.toAbsolutePath()
		);

		OrphanFileScanner linkedScanner = new OrphanFileScanner(
			mapper,
			storageRoot.toString(),
			"/free/",
			"/linked-gallery/",
			"/thumbnail/",
			24
		);

		givenReferences(List.of(), List.of());

		assertThatThrownBy(linkedScanner::scan)
			.isInstanceOf(IOException.class)
			.hasMessageContaining("안전한 디렉토리가 아닙니다");

		assertThat(outsideFile).hasContent("outside-data");
	}

	@Test
	@DisplayName("개별 재확인은 전체 메타데이터와 전체 작업 목록을 조회하지 않는다")
	void isStillCandidate_usesTargetedQueries() throws Exception {
		Path file = createOldFile("gallery/orphan.jpeg");

		OrphanFileCandidate candidate = new OrphanFileCandidate(
			relativePath(file),
			Files.size(file),
			Files.getLastModifiedTime(file).toInstant(),
			Reason.UNREFERENCED
		);

		given(mapper.existsPendingTask(relativePath(file)))
			.willReturn(false);

		given(mapper.findReferencesByFileName("orphan.jpeg"))
			.willReturn(List.of());

		assertThat(scanner.isStillCandidate(candidate)).isTrue();

		then(mapper).should(never()).findAllReferences();
		then(mapper).should(never()).findPendingTaskPaths();
	}

	@Test
	@DisplayName("탐지 이후 파일 크기가 변경되면 후보에서 제외한다")
	void isStillCandidate_fileChanged_returnsFalse() throws Exception {
		Path file = createOldFile("gallery/orphan.jpeg");

		OrphanFileCandidate candidate = new OrphanFileCandidate(
			relativePath(file),
			Files.size(file),
			Files.getLastModifiedTime(file).toInstant(),
			Reason.UNREFERENCED
		);

		Files.writeString(file, "changed-file-content-with-different-size");

		assertThat(scanner.isStillCandidate(candidate)).isFalse();

		then(mapper).shouldHaveNoInteractions();
	}



	// ── 테스트 도우미 ───────────────────────────────────

	private void givenReferences(
		List<StoredFileReference> references,
		List<String> pendingPaths
	) {
		given(mapper.findAllReferences()).willReturn(references);
		given(mapper.findPendingTaskPaths()).willReturn(pendingPaths);
	}

	private Path createOldFile(String relativePath) throws IOException {
		Path file = storageRoot.resolve(relativePath);

		Files.createDirectories(file.getParent());
		Files.writeString(file, "test-data");

		// 24시간 경계에서 흔들리지 않도록 48시간 전으로 설정한다.
		Files.setLastModifiedTime(
			file,
			FileTime.from(Instant.now().minus(Duration.ofHours(48)))
		);

		return file;
	}

	private String relativePath(Path file) {
		return storageRoot.relativize(file).toString();
	}

	private StoredFileReference reference(
		String path,
		String storeName,
		String extension,
		Boolean deleted
	) {
		StoredFileReference reference = new StoredFileReference();

		ReflectionTestUtils.setField(reference, "path", path);
		ReflectionTestUtils.setField(reference, "storeName", storeName);
		ReflectionTestUtils.setField(reference, "extension", extension);
		ReflectionTestUtils.setField(reference, "deleted", deleted);

		return reference;
	}
}