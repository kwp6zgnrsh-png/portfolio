package com.study.backend.file.cleanup.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.study.backend.file.cleanup.model.FileCleanupTask;

@Component
public class FileCleanupWorker {

	private final Path configuredRoot;

	public FileCleanupWorker(@Value("${store.base-path}") String storeBasePath) {
		if (storeBasePath == null || storeBasePath.isBlank()) {
			throw new IllegalArgumentException("파일 저장소 경로가 비어 있습니다.");
		}

		this.configuredRoot = Path.of(storeBasePath)
			.toAbsolutePath()
			.normalize();
	}

	/** 작업 종류에 따라 실제 파일을 이동하거나 삭제한다. */
	public void execute(FileCleanupTask task) throws IOException {
		if (task.getTaskType() == null) {
			throw new IllegalArgumentException("파일 정리 작업 종류가 없습니다.");
		}

		switch (task.getTaskType()) {
			case MOVE -> move(
				task.getSourcePath(),
				task.getDestinationPath()
			);
			case DELETE -> delete(task.getSourcePath());
		}
	}

	private void move(String sourcePath, String destinationPath) throws IOException {
		Path source = resolveSafePath(sourcePath);
		Path destination = resolveSafePath(destinationPath);

		if (source.equals(destination)) {
			throw new IOException("파일 이동의 원본과 목적지가 같습니다.");
		}

		boolean sourceExists = regularFileExists(source);
		boolean destinationExists = regularFileExists(destination);

		if (!sourceExists && destinationExists) {
			// 이전 실행에서 이동은 성공하고 DB 작업 완료 처리가
			// 실패한 경우에도 다시 실행할 수 있도록 한다.
			return;
		}

		if (!sourceExists) {
			throw new NoSuchFileException("이동 원본과 목적지 파일이 모두 없습니다: " + source);
		}

		if (destinationExists) {
			throw new IOException("이동 목적지에 파일이 이미 존재합니다: " + destination);
		}

		// 기존 파일을 덮어쓰지 않는다.
		Files.move(source, destination);
	}

	private void delete(String sourcePath) throws IOException {
		Path source = resolveSafePath(sourcePath);

		if (!regularFileExists(source)) {
			// 이미 삭제된 파일도 완료로 처리한다.
			return;
		}

		Files.deleteIfExists(source);
	}

	/**
	 * 저장소 내부 경로인지 확인한다.
	 * 하위 디렉토리의 심볼릭 링크를 허용하지 않는다.
	 */
	private Path resolveSafePath(String rawPath) throws IOException {
		if (rawPath == null || rawPath.isBlank()) {
			throw new IllegalArgumentException("파일 정리 경로가 비어 있습니다.");
		}

		Path relative = Path.of(rawPath).normalize();

		if (relative.isAbsolute()
			|| relative.toString().isEmpty()
			|| relative.startsWith("..")) {

			throw new IllegalArgumentException("유효하지 않은 파일 정리 상대 경로입니다.");
		}

		Path root = configuredRoot.toRealPath();
		Path target = root.resolve(relative).normalize();

		if (target.equals(root) || !target.startsWith(root)) {
			throw new IllegalArgumentException("파일 정리 대상이 저장소 밖에 있습니다.");
		}

		requireDirectory(root);

		Path current = root;
		Path parent = target.getParent();

		for (Path part : root.relativize(parent)) {
			current = current.resolve(part);
			requireDirectory(current);
		}

		return target;
	}

	/** 심볼릭 링크가 아닌 실제 디렉토리인지 확인한다. */
	private void requireDirectory(Path path) throws IOException {
		BasicFileAttributes attributes = Files.readAttributes(
			path,
			BasicFileAttributes.class,
			LinkOption.NOFOLLOW_LINKS
		);

		if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
			throw new IOException("유효하지 않은 파일 저장 디렉토리입니다: " + path);
		}
	}

	/**
	 * 일반 파일의 존재 여부를 확인한다.
	 * 권한 오류 등의 입출력 오류는 '파일 없음'으로 처리하지 않는다.
	 */
	private boolean regularFileExists(Path path) throws IOException {
		try {
			BasicFileAttributes attributes = Files.readAttributes(
				path,
				BasicFileAttributes.class,
				LinkOption.NOFOLLOW_LINKS
			);

			if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
				throw new IOException("정리 대상이 일반 파일이 아닙니다: " + path);
			}

			return true;
		} catch (NoSuchFileException e) {
			return false;
		}
	}
}