package com.study.backend.board.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.study.backend.board.dto.common.request.BoardUpdateRequest;
import com.study.backend.board.mapper.GalleryMapper;
import com.study.backend.board.model.Board;
import com.study.backend.board.model.BoardType;
import com.study.backend.file.event.FileCleanupEvent;
import com.study.backend.file.exception.FileException;
import com.study.backend.file.model.FileMetaData;
import com.study.backend.file.service.FileService;
import com.study.backend.file.service.FileServiceFactory;
import com.study.backend.file.util.PathUtils;
import com.study.backend.thumbnail.dto.SourceImage;
import com.study.backend.thumbnail.model.ThumbnailMetaData;
import com.study.backend.file.util.FileCleanupHelper;
import com.study.backend.thumbnail.service.ThumbnailService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GalleryService extends AbstractBoardService<GalleryMapper> {

	@Value("${store.base-path}")
	private String storePath;

	private final FileServiceFactory fileService;
	private final ThumbnailService thumbnailService;
	private final ApplicationEventPublisher eventPublisher;

	public GalleryService(GalleryMapper mapper, FileServiceFactory fileService, ThumbnailService thumbnailService, ApplicationEventPublisher eventPublisher) {
		super(mapper);
		this.fileService = fileService;
		this.thumbnailService = thumbnailService;
		this.eventPublisher = eventPublisher;
	}

	/** 갤러리 게시글을 등록한다. */
	@Transactional
	public void createPost(Board board, Long boardTypeId, Long memberId) {
		mapper.createPost(board, boardTypeId, memberId);
	}

	/** 갤러리 게시글, 원본 이미지, 썸네일을 하나의 성공 단위로 등록한다. */
	@Transactional
	public void createPostWithFilesAndThumbnail(Board board, Long boardTypeId, Long memberId, MultipartFile[] files) {
		mapper.createPost(board, boardTypeId, memberId);
		FileService fs = fileService.getFileService(BoardType.GALLERIES);
		try {
			createFilesAndThumbnail(board.getId(), files);
		} catch (RuntimeException e) {
			cleanupCreatedFiles(fs, board.getId());
			throw e;
		}
	}

	/** 파일 저장 + 썸네일 생성은 하나의 트랜잭션 — 썸네일 실패 시 파일 메타데이터도 롤백한다. */
	@Transactional
	public void createFilesAndThumbnail(Long boardId, MultipartFile[] files) {
		FileService fs = fileService.getFileService(BoardType.GALLERIES);
		fs.createFiles(boardId, files);

		FileMetaData firstFile = fs.getFirstFileByBoardId(boardId);
		if (firstFile == null) {
			throw new FileException("파일 저장에 실패했습니다.");
		}

		thumbnailService.saveThumbnail(buildThumbnail(firstFile), boardId);
	}

	/** 기존 파일·썸네일을 삭제하고 새 파일·썸네일과 함께 게시글을 수정한다. 작성자 본인만 가능하다. */
	@Transactional
	public void updatePost(Long boardId, BoardUpdateRequest board, Long memberId, MultipartFile[] files) {
		Board updateBoard = mapper.getPostById(boardId);
		validateOwnership(updateBoard, memberId, "수정할 수 있는 권한이 없습니다.");

		FileService fs = fileService.getFileService(BoardType.GALLERIES);

		List<Path> createdFiles = new ArrayList<>();
		FileCleanupHelper.registerRollbackCleanup(createdFiles);
		try {
			ThumbnailMetaData oldThumbnail = thumbnailService.getThumbnailByBoardId(boardId);
			thumbnailService.deleteThumbnail(boardId);

			fs.validateFileCountForUpdate(boardId, board.getDeleteFiles(), files);
			fs.deleteFiles(boardId, board.getDeleteFiles());
			if (files != null && files.length > 0) {
				FileCleanupHelper.addFiles(createdFiles, fs.createFiles(boardId, files));
			}

			FileMetaData firstFile = fs.getFirstFileByBoardId(boardId);
			if (firstFile == null) {
				throw new FileException("파일 저장에 실패했습니다.");
			}

			Path thumbnailPath = thumbnailService.saveThumbnail(buildThumbnail(firstFile), boardId);
			if (thumbnailPath != null) {
				createdFiles.add(thumbnailPath);
			}
			publishThumbnailDeleteEventIfChanged(oldThumbnail, thumbnailPath);
			mapper.updatePost(boardId, board, memberId);
		} catch (RuntimeException e) {
			FileCleanupHelper.cleanupFiles(createdFiles);
			throw e;
		}
	}

	/** 수정 폼에 필요한 게시글을 조회한다. 존재하지 않거나 작성자가 아니면 예외를 던진다. */
	public Board getPostForUpdate(Long boardId, Long memberId) {
		Board board = mapper.getPostForUpdate(boardId);
		validateOwnership(board, memberId, "수정할 수 없습니다.");
		return board;
	}

	/** 게시글을 삭제한다. 작성자 본인만 가능하다. */
	@Transactional
	public void deletePost(Long boardId, Long memberId) {
		Board board = mapper.getPostById(boardId);
		validateOwnership(board, memberId, "삭제할 수 있는 권한이 없습니다.");

		ThumbnailMetaData thumbnail = thumbnailService.getThumbnailByBoardId(boardId);

		mapper.deletePost(boardId, memberId);
		fileService.getFileService(BoardType.GALLERIES).deleteAllFilesByBoardId(boardId);
		thumbnailService.deleteThumbnail(boardId);

		if (thumbnail != null) {
			eventPublisher.publishEvent(FileCleanupEvent.forDelete(List.of(thumbnailFilePath(thumbnail))));
		}
	}

	/** 썸네일이 변경된 경우 이전 썸네일의 삭제 이벤트를 발행한다. */
	private void publishThumbnailDeleteEventIfChanged(ThumbnailMetaData oldThumbnail, Path newThumbnailPath) {
		if (oldThumbnail == null) {
			return;
		}

		String oldThumbnailPath = thumbnailFilePath(oldThumbnail);
		if (newThumbnailPath != null && oldThumbnailPath.equals(newThumbnailPath.toString())) {
			return;
		}
		eventPublisher.publishEvent(FileCleanupEvent.forDelete(List.of(oldThumbnailPath)));
	}

	/** 파일 메타데이터로 SourceImage 객체를 생성한다. */
	private SourceImage buildThumbnail(FileMetaData file) {
		return SourceImage.builder()
			.fileName(file.getFileName())
			.storeName(file.getStoreName())
			.path(file.getPath())
			.extension(file.getExtension())
			.build();
	}

	/**
	 * 게시글 생성 실패 시 이미 저장된 파일들을 정리한다.
	 * 정리 중 발생하는 예외는 로깅만 하고 전파하지 않는다.
	 */
	private void cleanupCreatedFiles(FileService fs, Long boardId) {
		List<FileMetaData> files;
		try {
			files = fs.getFilesByBoardId(boardId);
		} catch (RuntimeException e) {
			log.error("갤러리 생성 실패 후 파일 목록 조회 실패: boardId={}", boardId, e);
			return;
		}
		if (files == null || files.isEmpty()) {
			return;
		}
		for (FileMetaData file : files) {
			Path path = Path.of(
				fs.resolveAbsolutePath(file.getPath()),
				file.getStoreName() + file.getExtension()
			);
			try {
				Files.deleteIfExists(path);
			} catch (IOException cleanupException) {
				log.error("갤러리 생성 실패 후 파일 정리 실패: {}", path, cleanupException);
			}
		}
	}

	private String thumbnailFilePath(ThumbnailMetaData thumbnail) {
		return PathUtils.joinStoreFilePath(
			storePath,
			thumbnail.getPath(),
			thumbnail.getStoreName(),
			thumbnail.getExtension()
		);
	}

	@Override
	protected BoardType boardType() {
		return BoardType.GALLERIES;
	}
}
