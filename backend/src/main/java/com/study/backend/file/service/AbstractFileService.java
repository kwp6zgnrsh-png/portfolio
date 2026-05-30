package com.study.backend.file.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.study.backend.file.event.FileCleanupEvent;
import com.study.backend.file.exception.FileException;
import com.study.backend.file.exception.FileNotFoundException;
import com.study.backend.file.exception.InvalidFilePathException;
import com.study.backend.file.mapper.FileMapper;
import com.study.backend.file.model.DownloadFile;
import com.study.backend.file.model.FileMetaData;
import com.study.backend.file.util.PathUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractFileService implements FileService {

	protected final FileMapper fileMapper;
	private final ApplicationEventPublisher eventPublisher;

	public AbstractFileService(FileMapper fileMapper, ApplicationEventPublisher eventPublisher) {
		this.fileMapper = fileMapper;
		this.eventPublisher = eventPublisher;
	}

	/** 게시글 ID로 첨부 파일 메타데이터 목록을 조회한다. */
	@Override
	public List<FileMetaData> getFilesByBoardId(Long boardId) {
		return fileMapper.getFilesByBoardId(boardId);
	}

	/** 파일 ID로 파일 메타데이터를 조회한다. */
	@Override
	public FileMetaData getFileById(Long fileId, Long boardTypeId) {
		return fileMapper.getFileByIdAndBoardType(fileId, boardTypeId);
	}

	/** 파일 ID와 게시판 타입으로 다운로드 가능한 물리 파일과 메타데이터를 조회한다. */
	@Override
	public DownloadFile getDownloadFile(Long fileId, Long boardTypeId) {
		FileMetaData fileMetaData = getFileById(fileId, boardTypeId);
		if (fileMetaData == null) {
			throw new FileNotFoundException("존재하지 않는 파일입니다.");
		}

		try {
			String absolutePath = resolveAbsolutePath(fileMetaData.getPath());
			File file = new File(absolutePath, fileMetaData.getStoreName() + fileMetaData.getExtension());
			String canonicalFilePath = file.getCanonicalPath();
			String canonicalBasePath = new File(resolveAbsolutePath("")).getCanonicalPath();

			if (!canonicalFilePath.startsWith(canonicalBasePath + File.separator)) {
				throw new FileException("유효하지 않은 파일 경로입니다.");
			}
			if (!file.exists()) {
				throw new FileNotFoundException("존재하지 않는 파일입니다.");
			}
			return new DownloadFile(file, fileMetaData);
		} catch (IOException e) {
			throw new InvalidFilePathException("파일 경로 검증에 실패했습니다.", e);
		}
	}

	/** 게시글의 첫 번째 파일 메타데이터를 조회한다. 썸네일 생성 시 사용한다. */
	@Override
	public FileMetaData getFirstFileByBoardId(Long boardId) {
		return fileMapper.getFirstFileByBoardId(boardId);
	}

	/** 파일을 디스크에 저장하고 파일 메타데이터를 DB에 등록한다. */
	@Override
	@Transactional
	public List<Path> createFiles(Long boardId, MultipartFile[] files) {
		List<Path> writtenPaths = new ArrayList<>();
		try {
			for (MultipartFile multipartFile : files) {
				if (!multipartFile.isEmpty()) {
					String fileName = multipartFile.getOriginalFilename();
					if (fileName == null || fileName.isBlank()) {
						throw new FileException("파일명이 유효하지 않습니다.");
					}
					String[] storeNameAndExtension = generateStoreName(fileName);
					String uploadPath = (resolveAbsolutePath(getPath()) + storeNameAndExtension[0] + storeNameAndExtension[1]);

					FileMetaData fileMetaData = FileMetaData.builder()
						.fileName(fileName)
						.storeName(storeNameAndExtension[0])
						.extension(storeNameAndExtension[1])
						.path(getPath())
						.fileSize(multipartFile.getSize())
						.boardId(boardId)
						.build();

					File uploadFile = new File(uploadPath);
					multipartFile.transferTo(uploadFile);
					writtenPaths.add(uploadFile.toPath());

					fileMapper.createFile(fileMetaData);
				}
			}
			return writtenPaths;
		} catch (IOException | RuntimeException e) {
			for (Path writtenPath : writtenPaths) {
				try {
					Files.deleteIfExists(writtenPath);
				} catch (IOException deleteException) {
					log.error("업로드 실패 후 파일 정리 실패: {}", writtenPath, deleteException);
				}
			}
			if (e instanceof FileException fileException) {
				throw fileException;
			}
			throw new FileException("파일 저장 실패", e);
		}
	}

	/** 수정 요청에 포함된 삭제 대상 파일을 DB에서 제거하고, 물리 파일 삭제는 트랜잭션 커밋 후 이벤트로 처리한다. */
	@Override
	@Transactional
	public void deleteFiles(Long boardId, String[] removeFiles) {
		List<FileMetaData> deleteTargets = resolveDeleteTargets(getFilesByBoardId(boardId), removeFiles);
		deleteFileMetadataAndPublishMoveEvent(deleteTargets);
	}

	/** 게시글에 속한 파일을 전부 DB에서 삭제하고, 물리 파일 삭제는 트랜잭션 커밋 후 이벤트로 처리한다. */
	@Override
	@Transactional
	public void deleteAllFilesByBoardId(Long boardId) {
		List<FileMetaData> files = getFilesByBoardId(boardId);
		deleteFileMetadataAndPublishMoveEvent(files);
	}

	/** 파일 개수, 용량, 확장자를 일괄 검증한다. 초과 시 FileException을 던진다. */
	@Override
	public void validateFiles(MultipartFile[] files) {
		if(countNonEmptyFiles(files) > getMaxFileCount()) {
			throw new FileException("파일은 최대 " + getMaxFileCount() + "개까지 가능합니다.");
		}
		validateFileSizeAndType(files, getFileMaxSize(), getFileTypes());
	}

	/** 수정 후 최종 파일 수가 최대 허용 개수를 초과하지 않는지 검증한다. */
	@Override
	public void validateFileCountForUpdate(Long boardId, String[] deleteFileIds, MultipartFile[] files) {
		List<FileMetaData> existingFiles = getFilesByBoardId(boardId);
		if (existingFiles == null) {
			existingFiles = List.of();
		}
		int deleteCount = resolveDeleteTargets(existingFiles, deleteFileIds).size();
		int newFileCount = countNonEmptyFiles(files);
		int finalFileCount = existingFiles.size() - deleteCount + newFileCount;

		if (finalFileCount > getMaxFileCount()) {
			throw new FileException("파일은 최대 " + getMaxFileCount() + "개까지 가능합니다.");
		}
	}

	/** 파일 메타데이터를 삭제하고, 물리 파일 이동은 트랜잭션 커밋 후 이벤트로 처리한다. */
	private void deleteFileMetadataAndPublishMoveEvent(List<FileMetaData> files) {
		if (files == null || files.isEmpty()) {
			return;
		}
		List<FileCleanupEvent.FileMoveTask> tasks = new ArrayList<>();
		for (FileMetaData file : files) {
			deleteFileById(file.getId());
			String src = resolveStoredFilePath(file.getPath(), file.getStoreName(), file.getExtension());
			String dest = resolveStoredFilePath(getDeletedPath(), file.getStoreName(), file.getExtension());
			tasks.add(new FileCleanupEvent.FileMoveTask(src, dest));
		}
		eventPublisher.publishEvent(FileCleanupEvent.forMove(tasks));
	}

	/** 저장 경로, 저장명, 확장자를 실제 파일 경로로 조립한다. */
	private String resolveStoredFilePath(String path, String storeName, String extension) {
		return resolveAbsolutePath(path) + storeName + extension;
	}

	/** Content-Type 스푸핑 방지를 위해 파일 시그니처(magic bytes)로 실제 포맷을 검증한다. */
	private void validateMagicBytes(MultipartFile file, String type) {
		try (InputStream is = file.getInputStream()) {
			byte[] header = new byte[8];
			int read = is.read(header);
			if (read < 4 || !matchesMagicBytes(header, type)) throw new FileException("파일 형식 오류");
		} catch (IOException e) {
			throw new FileException("파일 형식 오류", e);
		}
	}

	/** 파일 헤더의 매직 바이트가 지정된 파일 타입과 일치하는지 확인한다. */
	private boolean matchesMagicBytes(byte[] h, String type) {
		return switch (type) {
			case "jpeg" -> h[0] == (byte) 0xFF && h[1] == (byte) 0xD8 && h[2] == (byte) 0xFF;
			case "png"  -> h[0] == (byte) 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47
				&& h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A;
			case "gif"  -> h[0] == 0x47 && h[1] == 0x49 && h[2] == 0x46 && h[3] == 0x38;
			case "zip"  -> h[0] == 0x50 && h[1] == 0x4B && h[2] == 0x03 && h[3] == 0x04;
			default -> false;
		};
	}

	/**
	 * 삭제 요청된 파일 ID들을 기존 파일 목록과 대조하여 삭제 대상을 반환한다.
	 * 중복 ID는 무시하고, 존재하지 않는 ID는 예외를 던진다.
	 */
	private List<FileMetaData> resolveDeleteTargets(List<FileMetaData> existingFiles, String[] deleteFileIds) {
		if (deleteFileIds == null || deleteFileIds.length == 0) {
			return List.of();
		}
		if (existingFiles == null || existingFiles.isEmpty()) {
			throw new FileException("파일이 일치하지 않습니다.");
		}

		Map<Long, FileMetaData> existingFileMap = existingFiles.stream()
			.collect(Collectors.toMap(FileMetaData::getId, file -> file));
		Set<Long> requestedFileIds = new HashSet<>();
		List<FileMetaData> deleteTargets = new ArrayList<>();

		for (String deleteFileId : deleteFileIds) {
			long fileId = parseFileId(deleteFileId);
			if (!requestedFileIds.add(fileId)) {
				continue;
			}
			FileMetaData file = existingFileMap.get(fileId);
			if (file == null) {
				throw new FileException("파일이 일치하지 않습니다.");
			}
			deleteTargets.add(file);
		}
		return deleteTargets;
	}

	/** 문자열 파일 ID를 long으로 파싱한다. */
	private long parseFileId(String fileId) {
		try {
			return Long.parseLong(fileId);
		} catch (NumberFormatException e) {
			throw new FileException("유효하지 않은 파일 ID입니다.");
		}
	}

	/** Content-Type에서 MIME subtype을 추출한다. */
	private String extractMimeSubtype(MultipartFile file) {
		String contentType = file.getContentType();
		if (contentType == null) {
			throw new FileException("파일 형식 오류");
		}

		try {
			return MediaType.parseMediaType(contentType).getSubtype();
		} catch (InvalidMediaTypeException e) {
			throw new FileException("파일 형식 오류", e);
		}
	}

	/** DB에서 파일 메타데이터를 삭제한다. */
	protected void deleteFileById(Long fileId) {
		fileMapper.deleteFileById(fileId);
	}

	/** 원본 파일명에서 확장자를 분리하고 UUID로 저장명을 생성한다. [storeName, extension] 형태로 반환한다. */
	protected String[] generateStoreName(String fileName) {
		int index = fileName.lastIndexOf('.');
		if (index < 0) throw new FileException("확장자가 없는 파일은 업로드할 수 없습니다.");
		String extension = fileName.substring(index);
		String storeName = UUID.randomUUID().toString();
		return new String[]{storeName, extension};
	}

	/** 각 파일의 용량과 MIME 타입을 검증한다. 초과 또는 허용되지 않은 형식이면 예외를 던진다. */
	protected void validateFileSizeAndType(MultipartFile[] files, Long fileMaxSize, Set<String> fileTypes) {
		if (files == null) {
			return;
		}
		for (MultipartFile file : files) {
			if (!file.isEmpty()) {
				if (file.getSize() > fileMaxSize) {
					throw new FileException("파일 용량 초과");
				}

				String type = extractMimeSubtype(file);
				if (!fileTypes.contains(type)) {
					throw new FileException("파일 형식 오류");
				}
				validateMagicBytes(file, type);
			}
		}
	}

	/** 비어 있지 않은 파일의 개수를 반환한다. */
	protected int countNonEmptyFiles(MultipartFile[] files) {
		if (files == null || files.length == 0) {
			return 0;
		}
		return (int) Arrays.stream(files).filter(file -> !file.isEmpty()).count();
	}

	protected String joinStorePath(String storePath, String path) {
		return PathUtils.joinStorePath(storePath, path);
	}

	protected abstract String getPath();
	protected abstract String getDeletedPath();
	protected abstract Long getFileMaxSize();
	protected abstract Set<String> getFileTypes();
	protected abstract int getMaxFileCount();
}
