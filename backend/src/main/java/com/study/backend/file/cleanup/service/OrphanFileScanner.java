package com.study.backend.file.cleanup.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.study.backend.file.cleanup.mapper.OrphanFileMapper;
import com.study.backend.file.cleanup.model.OrphanFileCandidate;
import com.study.backend.file.cleanup.model.OrphanFileCandidate.Reason;
import com.study.backend.file.cleanup.model.StoredFileReference;
import com.study.backend.file.util.PathUtils;

@Service
public class OrphanFileScanner {

	private final OrphanFileMapper mapper;
	private final Path storageRoot;
	private final List<String> scanDirectories;
	private final Duration minimumAge;

	public OrphanFileScanner(
		OrphanFileMapper mapper,
		@Value("${store.base-path}") String storeBasePath,
		@Value("${free-board.path}") String freeBoardPath,
		@Value("${gallery-board.picture.path}") String galleryPath,
		@Value("${gallery-board.thumbnail.path}") String thumbnailPath,
		@Value("${file.orphan.minimum-age-hours:24}") long minimumAgeHours) {

		if (storeBasePath == null || storeBasePath.isBlank()) {
			throw new IllegalArgumentException("저장소 경로가 비어 있습니다.");
		}

		if (minimumAgeHours < 1) {
			throw new IllegalArgumentException("고아 파일 후보의 최소 경과 시간은 1시간 이상이어야 합니다.");
		}

		this.mapper = mapper;
		this.storageRoot = Path.of(storeBasePath)
			.toAbsolutePath()
			.normalize();

		this.scanDirectories = List.of(
			freeBoardPath,
			galleryPath,
			thumbnailPath
		);

		this.minimumAge = Duration.ofHours(minimumAgeHours);
	}

	/**
	 * 파일을 변경하지 않고 정리 후보만 반환한다.
	 * DB 또는 파일 조회 실패 시 예외를 전파하여 실행을 중단한다.
	 */
	public List<OrphanFileCandidate> scan() throws IOException {
		Set<Path> protectedPaths = new HashSet<>();
		Set<Path> deletedPaths = new HashSet<>();

		// DB 조회 실패를 빈 목록으로 바꾸지 않는다.
		for (StoredFileReference reference : mapper.findAllReferences()) {
			Path file = resolveMetadataPath(reference);

			if (Boolean.TRUE.equals(reference.getDeleted())) {
				deletedPaths.add(file);
			} else {
				protectedPaths.add(file);
			}
		}

		for (String taskPath : mapper.findPendingTaskPaths()) {
			protectedPaths.add(resolveTaskPath(taskPath));
		}

		Instant cutoff = Instant.now().minus(minimumAge);
		Set<Path> visited = new HashSet<>();
		List<OrphanFileCandidate> candidates = new ArrayList<>();

		for (String directory : scanDirectories) {
			Path scanDirectory = resolveConfiguredDirectory(directory);
			requireSafeDirectory(scanDirectory);

			try (DirectoryStream<Path> entries = Files.newDirectoryStream(scanDirectory)) {
				for (Path entry : entries) {
					Path file = entry.toAbsolutePath().normalize();

					if (!visited.add(file)) {
						continue;
					}

					BasicFileAttributes attributes = Files.readAttributes(
						file,
						BasicFileAttributes.class,
						LinkOption.NOFOLLOW_LINKS
					);


					// 하위 폴더나 심볼릭 링크는 탐색하지 않는다.
					if (!attributes.isRegularFile()
						|| attributes.isSymbolicLink()) {
						continue;
					}

					if (protectedPaths.contains(file)) {
						continue;
					}

					Instant modifiedAt =
						attributes.lastModifiedTime().toInstant();

					if (modifiedAt.isAfter(cutoff)) {
						continue;
					}

					Reason reason = deletedPaths.contains(file)
						? Reason.DELETED_METADATA
						: Reason.UNREFERENCED;

					candidates.add(new OrphanFileCandidate(
						storageRoot.relativize(file).toString(),
						attributes.size(),
						modifiedAt,
						reason
					));
				}
			}
		}

		return List.copyOf(candidates);
	}

	/**
	 * 후보 파일 하나의 상태만 재확인한다.
	 * 파일 변경·활성 참조·대기 작업이 있으면 false를 반환한다.
	 * DB 오류와 파일 접근 오류는 전파한다.
	 */
	public boolean isStillCandidate(
		OrphanFileCandidate candidate
	) throws IOException {
		if (candidate == null) {
			throw new IllegalArgumentException("고아 파일 후보가 없습니다.");
		}

		Path file = resolveTaskPath(candidate.relativePath());
		Path parent = file.getParent();

		// 최초 탐지와 동일하게 관리 폴더 바로 아래의 파일만 허용한다.
		boolean managedDirectory = scanDirectories.stream()
			.map(this::resolveConfiguredDirectory)
			.anyMatch(parent::equals);

		if (!managedDirectory) {
			throw new IllegalArgumentException(
				"고아 파일 후보가 관리 폴더에 속하지 않습니다."
			);
		}

		BasicFileAttributes attributes;

		try {
			// 중간 디렉토리의 심볼릭 링크도 검사한다.
			requireSafeDirectory(parent);

			attributes = Files.readAttributes(
				file,
				BasicFileAttributes.class,
				LinkOption.NOFOLLOW_LINKS
			);
		} catch (NoSuchFileException e) {
			// 탐지 이후 파일 또는 상위 디렉토리가 없어졌다면 등록하지 않는다.
			return false;
		}

		if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
			return false;
		}

		Instant modifiedAt = attributes.lastModifiedTime().toInstant();

		if (attributes.size() != candidate.fileSize()
			|| !modifiedAt.equals(candidate.lastModifiedAt())) {
			return false;
		}

		if (modifiedAt.isAfter(Instant.now().minus(minimumAge))) {
			return false;
		}

		String relativePath = storageRoot.relativize(file).toString();

		if (mapper.existsPendingTask(relativePath)) {
			return false;
		}

		List<StoredFileReference> references =
			mapper.findReferencesByFileName(
				file.getFileName().toString()
			);

		boolean deletedReferenceFound = false;

		for (StoredFileReference reference : references) {
			Path referencedFile = resolveMetadataPath(reference);

			// 같은 파일명이 다른 폴더에 있을 수도 있으므로 경로까지 비교한다.
			if (!file.equals(referencedFile)) {
				continue;
			}

			// 활성 참조가 하나라도 있으면 보존한다.
			// deleted가 null인 경우도 기존 정책대로 보존한다.
			if (!Boolean.TRUE.equals(reference.getDeleted())) {
				return false;
			}

			deletedReferenceFound = true;
		}

		Reason currentReason = deletedReferenceFound
			? Reason.DELETED_METADATA
			: Reason.UNREFERENCED;

		return currentReason == candidate.reason();
	}

	private Path resolveMetadataPath(StoredFileReference reference) {
		String name = reference.getStoreName();
		String extension = reference.getExtension();

		if (name == null || name.isBlank() || extension == null) {
			throw new IllegalArgumentException("파일 메타데이터의 저장명이 유효하지 않습니다.");
		}

		String fileName = name + extension;

		if (fileName.contains("/") || fileName.contains("\\")) {
			throw new IllegalArgumentException("저장 파일명에 경로 구분자가 포함되어 있습니다.");
		}

		Path directory = resolveConfiguredDirectory(reference.getPath());

		return requireInsideStorage(directory.resolve(fileName).normalize());
	}

	/**
	 * 기존 설정과 메타데이터의 '/gallery/' 같은 값을
	 * 저장소 기준 경로로 해석한다.
	 */
	private Path resolveConfiguredDirectory(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("파일 디렉토리가 비어 있습니다.");
		}

		String relative = PathUtils.removeLeadingSlash(value);

		Path path = Path.of(relative).normalize();

		if (path.isAbsolute() || path.startsWith("..")) {
			throw new IllegalArgumentException("유효하지 않은 파일 디렉토리입니다.");
		}

		return requireInsideStorage(storageRoot.resolve(path).normalize());
	}

	/** 작업 테이블의 경로는 처음부터 상대 경로여야 한다. */
	private Path resolveTaskPath(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("정리 작업 경로가 비어 있습니다.");
		}

		Path path = Path.of(value).normalize();

		if (path.isAbsolute() || path.startsWith("..")) {
			throw new IllegalArgumentException("유효하지 않은 정리 작업 경로입니다.");
		}

		return requireInsideStorage(storageRoot.resolve(path).normalize());
	}

	private Path requireInsideStorage(Path target) {
		if (target.equals(storageRoot) || !target.startsWith(storageRoot)) {
			throw new IllegalArgumentException("저장소 내부 경로가 아닙니다.");
		}

		return target;
	}

	/** 탐색 디렉토리의 중간 경로에 심볼릭 링크가 있는지도 확인한다. */
	private void requireSafeDirectory(Path directory) throws IOException {
		Path current = storageRoot;
		requireDirectory(current);

		for (Path part : storageRoot.relativize(directory)) {
			current = current.resolve(part);
			requireDirectory(current);
		}
	}

	private void requireDirectory(Path path) throws IOException {
		BasicFileAttributes attributes = Files.readAttributes(
			path,
			BasicFileAttributes.class,
			LinkOption.NOFOLLOW_LINKS
		);

		if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
			throw new IOException("탐색 대상이 안전한 디렉토리가 아닙니다: " + path);
		}
	}
}