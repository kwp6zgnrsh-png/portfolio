package com.study.backend.file.cleanup.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.study.backend.file.cleanup.mapper.OrphanFileMapper;
import com.study.backend.file.cleanup.model.OrphanFileCandidate;
import com.study.backend.file.util.PathUtils;

@Service
public class OrphanFileRecoveryService {

	private final OrphanFileScanner scanner;
	private final OrphanFileMapper orphanMapper;
	private final FileCleanupTaskService taskService;

	private final Path storageRoot;
	private final Path freeDirectory;
	private final Path galleryDirectory;
	private final Path thumbnailDirectory;
	private final Path freeDeletedDirectory;
	private final Path galleryDeletedDirectory;

	public OrphanFileRecoveryService(
		OrphanFileScanner scanner,
		OrphanFileMapper orphanMapper,
		FileCleanupTaskService taskService,
		@Value("${store.base-path}") String storeBasePath,
		@Value("${free-board.path}") String freePath,
		@Value("${gallery-board.picture.path}") String galleryPath,
		@Value("${gallery-board.thumbnail.path}") String thumbnailPath,
		@Value("${free-board.deleted-path}") String freeDeletedPath,
		@Value("${gallery-board.picture.deleted-path}") String galleryDeletedPath
	) {
		this.scanner = scanner;
		this.orphanMapper = orphanMapper;
		this.taskService = taskService;

		if (storeBasePath == null || storeBasePath.isBlank()) {
			throw new IllegalArgumentException("저장소 경로가 비어 있습니다.");
		}

		this.storageRoot = Path.of(storeBasePath)
			.toAbsolutePath()
			.normalize();

		this.freeDirectory = configuredRelativePath(freePath);
		this.galleryDirectory = configuredRelativePath(galleryPath);
		this.thumbnailDirectory = configuredRelativePath(thumbnailPath);
		this.freeDeletedDirectory = configuredRelativePath(freeDeletedPath);
		this.galleryDeletedDirectory =
			configuredRelativePath(galleryDeletedPath);
	}

	/**
	 * 시간 기반 정책에 따라 후보 하나를 재확인하고 MOVE 작업을 등록한다.
	 * 후보 변경·기존 작업·목적지 충돌이 있으면 등록하지 않는다.
	 * 정상 업로드가 24시간 이내에 완료되고 UUID 경로를 재사용하지 않는
	 * 단일 서버 환경을 전제로 한다.
	 */
	@Transactional(rollbackFor = Exception.class)
	public boolean enqueueMoveIfStillCandidate(
		OrphanFileCandidate candidate
	) throws IOException {
		if (candidate == null) {
			throw new IllegalArgumentException("고아 파일 후보가 없습니다.");
		}

		// 전체 재탐색 대신 후보 파일 하나만 확인한다.
		if (!scanner.isStillCandidate(candidate)) {
			return false;
		}

		Path source = Path.of(candidate.relativePath()).normalize();
		Path destinationDirectory = destinationDirectory(source);
		Path destination = destinationDirectory.resolve(
			source.getFileName()
		);

		if (source.equals(destination)) {
			throw new IllegalArgumentException(
				"원본과 이동 목적지가 같습니다."
			);
		}

		// 전체 대기 작업 조회 대신 목적지 경로 하나만 확인한다.
		if (orphanMapper.existsPendingTask(destination.toString())) {
			return false;
		}

		Path absoluteDestination = storageRoot.resolve(destination);

		if (entryExists(absoluteDestination)) {
			return false;
		}

		taskService.enqueueMove(
			source.toString(),
			destination.toString()
		);

		return true;
	}

	private Path destinationDirectory(Path source) {
		Path parent = source.getParent();

		if (freeDirectory.equals(parent)) {
			return freeDeletedDirectory;
		}

		if (galleryDirectory.equals(parent)
			|| thumbnailDirectory.equals(parent)) {
			return galleryDeletedDirectory;
		}

		throw new IllegalArgumentException("지원하지 않는 고아 파일 위치입니다.");
	}

	private Path configuredRelativePath(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("파일 경로 설정이 비어 있습니다.");
		}

		Path path = Path.of(
			PathUtils.removeLeadingSlash(value)
		).normalize();

		if (path.isAbsolute()
			|| path.toString().isEmpty()
			|| path.startsWith("..")) {
			throw new IllegalArgumentException("유효하지 않은 파일 경로 설정입니다.");
		}

		return path;
	}

	private boolean entryExists(Path path) throws IOException {
		try {
			Files.readAttributes(
				path,
				BasicFileAttributes.class,
				LinkOption.NOFOLLOW_LINKS
			);
			return true;
		} catch (NoSuchFileException e) {
			return false;
		}
	}
}